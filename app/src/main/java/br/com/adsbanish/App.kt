package br.com.adsbanish

import android.app.Application
import br.com.adsbanish.blocklist.BlocklistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {

    val repository: BlocklistRepository by lazy { BlocklistRepository(this) }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        scope.launch { repository.loadIntoMemory() }
    }
}
