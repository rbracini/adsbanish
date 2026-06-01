package br.com.adsbanish.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import br.com.adsbanish.blocklist.BlocklistRepository
import br.com.adsbanish.ui.theme.ADSBanishTheme
import br.com.adsbanish.vpn.AdBlockVpnService
import br.com.adsbanish.vpn.VpnState

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) startVpnService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ADSBanishTheme {
                MainScreen(
                    vpnState    = AdBlockVpnService.state,
                    viewModel   = viewModel,
                    onVpnToggle = ::handleVpnToggle
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Fallback: se o VPN deveria estar ativo (flag salva) mas não está, reinicia automaticamente.
        // Cobre casos onde o BootReceiver foi bloqueado pelo sistema/OEM.
        val prefs = getSharedPreferences(BlocklistRepository.PREFS_NAME, Context.MODE_PRIVATE)
        val shouldBeActive = prefs.getBoolean(AdBlockVpnService.KEY_VPN_ENABLED, false)
        val isActive = AdBlockVpnService.state.value is VpnState.Active
        if (shouldBeActive && !isActive) {
            Log.d("ADSBanish", "onResume: VPN deveria estar ativa — reiniciando")
            viewModel.notifyAutoStartNeeded()
            if (VpnService.prepare(this) == null) startVpnService()
        }
    }

    private fun handleVpnToggle(currentState: VpnState) {
        Log.d("ADSBanish", "handleVpnToggle: currentState=$currentState")
        when (currentState) {
            is VpnState.Active -> {
                Log.d("ADSBanish", "handleVpnToggle: branch ACTIVE -> solicitando parada")
                getSharedPreferences(BlocklistRepository.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(AdBlockVpnService.KEY_VPN_ENABLED, false).apply()
                AdBlockVpnService.requestStop()
                Log.d("ADSBanish", "handleVpnToggle: requestStop() chamado")
            }
            else -> {
                Log.d("ADSBanish", "handleVpnToggle: branch ELSE -> iniciando VPN")
                val prepareIntent = VpnService.prepare(this)
                if (prepareIntent != null) vpnPermissionLauncher.launch(prepareIntent)
                else startVpnService()
            }
        }
    }

    private fun startVpnService() {
        startForegroundService(Intent(this, AdBlockVpnService::class.java))
    }
}
