package br.com.adsbanish.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import br.com.adsbanish.App
import br.com.adsbanish.R
import br.com.adsbanish.blocklist.BlocklistRepository
import br.com.adsbanish.blocklist.BlocklistRepository.Companion.PREFS_NAME
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AdBlockVpnService : VpnService() {

    companion object {
        private const val CHANNEL_ID      = "adblocker_vpn"
        private const val NOTIFICATION_ID = 1
        const val KEY_VPN_ENABLED = "vpn_enabled"

        private val _state = MutableStateFlow<VpnState>(VpnState.Inactive)
        val state: StateFlow<VpnState> = _state

        // Referência à instância ativa — permite chamar stopSelf() diretamente sem IPC
        @Volatile private var instance: AdBlockVpnService? = null

        fun requestStop() {
            Log.d("ADSBanish", "requestStop: instance=$instance")
            val svc = instance
            if (svc != null) {
                // Fechar o TUN desbloqueia imediatamente o PacketProcessor que está em read() bloqueante
                svc.tunInterface?.close()
                svc.tunInterface = null
                ServiceCompat.stopForeground(svc, ServiceCompat.STOP_FOREGROUND_REMOVE)
                svc.stopSelf()
                Log.d("ADSBanish", "requestStop: stopSelf() chamado")
            } else {
                Log.w("ADSBanish", "requestStop: sem instância ativa — serviço já parado?")
            }
        }
    }

    private var scope        = CoroutineScope(Dispatchers.IO + SupervisorJob())
    var tunInterface         : android.os.ParcelFileDescriptor? = null
    private lateinit var repository: BlocklistRepository

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d("ADSBanish", "onCreate: instância registrada")
        repository = (application as App).repository
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ADSBanish", "onStartCommand: intent=${intent?.action}, startId=$startId")

        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_VPN_ENABLED, true).apply()

        startForeground(NOTIFICATION_ID, buildNotification())

        // Se já há uma interface ativa (restart pelo START_STICKY), não recria
        if (tunInterface != null) {
            Log.d("ADSBanish", "onStartCommand: TUN já existe, ignorando recriação")
            return START_STICKY
        }

        try {
            val tun = Builder()
                .setSession("ADSBanish")
                .addAddress("10.10.0.1", 24)
                // DNS em 10.10.0.2 (diferente do IP do TUN 10.10.0.1).
                // Pacotes ao próprio IP do TUN são tratados como locais pelo kernel e não chegam no fd.
                // Usando 10.10.0.2 como DNS, os pacotes são roteados normalmente pelo TUN fd.
                .addRoute("10.10.0.0", 24)
                .addDnsServer("10.10.0.2")
                .setMtu(1500)
                .setBlocking(true)
                .establish() ?: throw IllegalStateException("Falha ao criar interface VPN")

            tunInterface = tun
            _state.value = VpnState.Active
            Log.d("ADSBanish", "onStartCommand: VPN estabelecida, estado=Active")

            scope.launch {
                Log.d("ADSBanish", "PacketProcessor: iniciando")
                PacketProcessor(
                    vpnService = this@AdBlockVpnService,
                    tunFd      = tun.fileDescriptor,
                    isBlocked  = { domain -> repository.isBlocked(domain) }
                ).run()
                Log.d("ADSBanish", "PacketProcessor: encerrado")
            }

        } catch (e: Exception) {
            Log.e("ADSBanish", "onStartCommand: erro ao criar VPN", e)
            _state.value = VpnState.Error(e.message ?: "Erro desconhecido")
            stopSelf()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d("ADSBanish", "onDestroy: serviço sendo destruído")
        instance = null
        scope.cancel()
        tunInterface?.close()
        tunInterface = null
        _state.value = VpnState.Inactive
        Log.d("ADSBanish", "onDestroy: estado=Inactive")
        super.onDestroy()
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ADSBanish ativo")
            .setContentText(
                repository.domainCount.takeIf { it > 0 }
                    ?.let { "Bloqueando $it domínios" } ?: "Bloqueando anúncios"
            )
            .setSmallIcon(R.drawable.ic_shield)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "ADSBanish VPN", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Bloqueio ativo de anúncios" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
