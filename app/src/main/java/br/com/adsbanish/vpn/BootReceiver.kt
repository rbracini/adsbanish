package br.com.adsbanish.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import androidx.core.app.NotificationCompat
import br.com.adsbanish.R
import br.com.adsbanish.blocklist.BlocklistRepository
import br.com.adsbanish.ui.MainActivity

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d("ADSBanish", "BootReceiver: BOOT_COMPLETED recebido")

        val prefs   = context.getSharedPreferences(BlocklistRepository.PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(AdBlockVpnService.KEY_VPN_ENABLED, false)
        Log.d("ADSBanish", "BootReceiver: vpn_enabled=$enabled")

        if (!enabled) return

        // Verifica se a permissão de VPN ainda está concedida
        val needsPermission = VpnService.prepare(context) != null
        if (needsPermission) {
            Log.w("ADSBanish", "BootReceiver: permissão VPN necessária — usuário deve abrir o app")
            showNotification(
                context,
                title = "ADSBanish precisa de permissão",
                text  = "Toque para reativar o bloqueio de anúncios"
            )
            return
        }

        try {
            context.startForegroundService(Intent(context, AdBlockVpnService::class.java))
            Log.d("ADSBanish", "BootReceiver: serviço iniciado com sucesso")
        } catch (e: Exception) {
            Log.e("ADSBanish", "BootReceiver: falha ao iniciar serviço", e)
            showNotification(
                context,
                title = "ADSBanish não iniciou automaticamente",
                text  = "Toque para reativar. Se persistir: Config → Apps → ADSBanish → Inicialização automática"
            )
        }
    }

    private fun showNotification(context: Context, title: String, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("boot_fail", "ADSBanish", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Falha no auto-start" }
        )
        val pi = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
            PendingIntent.FLAG_IMMUTABLE
        )
        nm.notify(
            2,
            NotificationCompat.Builder(context, "boot_fail")
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setSmallIcon(R.drawable.ic_shield)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        )
    }
}
