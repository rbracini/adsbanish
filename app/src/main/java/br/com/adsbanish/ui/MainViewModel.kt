package br.com.adsbanish.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.adsbanish.App
import br.com.adsbanish.blocklist.BlocklistSource
import br.com.adsbanish.vpn.AdBlockVpnService
import br.com.adsbanish.vpn.VpnState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class DownloadState {
    object Idle : DownloadState()
    data class Loading(val progress: Int) : DownloadState()
    object Success : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as App).repository

    private val _downloadState  = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState

    private val _downloadStatus = MutableStateFlow("")
    val downloadStatus: StateFlow<String> = _downloadStatus

    private val _domainCount = MutableStateFlow(repository.domainCount)
    val domainCount: StateFlow<Int> = _domainCount

    private val _lastUpdate = MutableStateFlow(repository.lastUpdateFormatted)
    val lastUpdate: StateFlow<String> = _lastUpdate

    private val _uptime = MutableStateFlow("--:--:--")
    val uptime: StateFlow<String> = _uptime

    private val _autoStartHint = MutableStateFlow(false)
    val autoStartHint: StateFlow<Boolean> = _autoStartHint

    private val _sourcesEnabled = MutableStateFlow(
        BlocklistSource.entries.associateWith { repository.isSourceEnabled(it) }
    )
    val sourcesEnabled: StateFlow<Map<BlocklistSource, Boolean>> = _sourcesEnabled

    private val _sourceDomainCounts = MutableStateFlow(
        BlocklistSource.entries.associateWith { repository.getSourceDomainCount(it) }
    )
    val sourceDomainCounts: StateFlow<Map<BlocklistSource, Int>> = _sourceDomainCounts

    private var uptimeJob: Job? = null
    private val sourceMutex = Mutex()

    init {
        viewModelScope.launch {
            AdBlockVpnService.state.collect { vpnState ->
                if (vpnState is VpnState.Active) {
                    startUptimeTimer()
                    _autoStartHint.value = false
                } else {
                    stopUptimeTimer()
                }
            }
        }
    }

    private fun startUptimeTimer() {
        uptimeJob?.cancel()
        uptimeJob = viewModelScope.launch {
            while (isActive) {
                val t0  = AdBlockVpnService.startTimeMs
                val sec = if (t0 > 0L) (System.currentTimeMillis() - t0) / 1_000 else 0L
                _uptime.value = formatUptime(sec)
                delay(1_000)
            }
        }
    }

    private fun stopUptimeTimer() {
        uptimeJob?.cancel()
        uptimeJob = null
        _uptime.value = "--d --:--"
    }

    // < 24h → HH:MM:SS · ≥ 1d → Dd HH:MM · ≥ 100d → Dd HHh
    private fun formatUptime(sec: Long): String {
        val d = sec / 86400
        val h = (sec % 86400) / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        val pad = { n: Long -> n.toString().padStart(2, '0') }
        return when {
            d == 0L  -> "${pad(h)}:${pad(m)}:${pad(s)}"
            d < 100L -> "${d}d ${pad(h)}:${pad(m)}"
            else     -> "${d}d ${pad(h)}h"
        }
    }

    fun updateBlocklist() {
        if (_downloadState.value is DownloadState.Loading) return
        viewModelScope.launch {
            try {
                _downloadState.value  = DownloadState.Loading(0)
                _downloadStatus.value = ""
                repository.downloadAllAndUpdate(
                    onProgress = { progress -> _downloadState.value = DownloadState.Loading(progress) },
                    onStatus   = { status   -> _downloadStatus.value = status }
                )
                _domainCount.value        = repository.domainCount
                _lastUpdate.value         = repository.lastUpdateFormatted
                _sourceDomainCounts.value = BlocklistSource.entries.associateWith { repository.getSourceDomainCount(it) }
                _downloadState.value      = DownloadState.Success
                _downloadStatus.value     = ""
            } catch (e: Exception) {
                _downloadState.value  = DownloadState.Error(e.message ?: "Erro desconhecido")
                _downloadStatus.value = ""
            }
        }
    }

    fun toggleSource(source: BlocklistSource, enabled: Boolean) {
        _sourcesEnabled.value = _sourcesEnabled.value.toMutableMap().apply { put(source, enabled) }
        viewModelScope.launch {
            sourceMutex.withLock {
                repository.setSourceEnabled(source, enabled)
                _domainCount.value = repository.activeDomainCount
            }
        }
    }

    fun refreshDomainCount() {
        _domainCount.value = repository.activeDomainCount.takeIf { it > 0 } ?: repository.domainCount
    }

    val hasDownloadedList: Boolean get() = repository.hasDownloadedList

    fun dismissDownload() { if (_downloadState.value !is DownloadState.Loading) _downloadState.value = DownloadState.Idle }

    fun notifyAutoStartNeeded() { _autoStartHint.value = true }
    fun dismissAutoStartHint()  { _autoStartHint.value = false }
}
