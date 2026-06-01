package br.com.adsbanish.vpn

sealed class VpnState {
    object Inactive : VpnState()
    object Active : VpnState()
    data class Error(val message: String) : VpnState()
}
