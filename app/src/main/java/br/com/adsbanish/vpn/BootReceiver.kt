package br.com.adsbanish.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.LOCKED_BOOT_COMPLETED") return

        Log.d("ADSBanish", "BootReceiver: ação=$action")
        val prefs = context.getSharedPreferences("adblocker_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("vpn_enabled", false)
        Log.d("ADSBanish", "BootReceiver: vpn_enabled=$enabled")

        if (enabled) {
            try {
                context.startForegroundService(Intent(context, AdBlockVpnService::class.java))
                Log.d("ADSBanish", "BootReceiver: serviço iniciado")
            } catch (e: Exception) {
                Log.e("ADSBanish", "BootReceiver: falha ao iniciar serviço", e)
            }
        }
    }
}
