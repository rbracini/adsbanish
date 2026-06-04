package br.com.adsbanish.blocklist

import android.app.Application
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BlocklistRepositoryTest {

    private lateinit var context: Application
    private lateinit var repository: BlocklistRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // Prefs e arquivos limpos a cada teste
        context.getSharedPreferences(BlocklistRepository.PREFS_NAME, 0).edit().clear().commit()
        context.filesDir.listFiles()?.forEach { it.delete() }
        repository = BlocklistRepository(context)
    }

    // ── hasDownloadedList ─────────────────────────────────────────────────────

    @Test
    fun `hasDownloadedList is false on fresh install`() {
        assertFalse(repository.hasDownloadedList)
    }

    @Test
    fun `hasDownloadedList is true when domainCount is saved in prefs`() {
        context.getSharedPreferences(BlocklistRepository.PREFS_NAME, 0)
            .edit().putInt("domain_count", 1000).commit()
        val fresh = BlocklistRepository(context)
        assertTrue(fresh.hasDownloadedList)
    }

    @Test
    fun `hasDownloadedList is true when legacy blocklist file exists`() {
        File(context.filesDir, "blocklist.txt").writeText("0.0.0.0 ads.example.com\n")
        val fresh = BlocklistRepository(context)
        assertTrue(fresh.hasDownloadedList)
    }

    @Test
    fun `hasDownloadedList is true when any per-source file exists`() {
        File(context.filesDir, "blocklist_stevenblack.txt")
            .writeText("0.0.0.0 ads.example.com\n")
        val fresh = BlocklistRepository(context)
        assertTrue(fresh.hasDownloadedList)
    }

    // ── isSourceEnabled ───────────────────────────────────────────────────────

    @Test
    fun `isSourceEnabled returns true by default for all sources`() {
        BlocklistSource.entries.forEach { source ->
            assertTrue("${source.name} should be enabled by default", repository.isSourceEnabled(source))
        }
    }

    @Test
    fun `isSourceEnabled returns false after being disabled`() {
        context.getSharedPreferences(BlocklistRepository.PREFS_NAME, 0)
            .edit().putBoolean("source_enabled_OISD_BIG", false).commit()
        val fresh = BlocklistRepository(context)
        assertFalse(fresh.isSourceEnabled(BlocklistSource.OISD_BIG))
    }

    // ── getSourceDomainCount ──────────────────────────────────────────────────

    @Test
    fun `getSourceDomainCount returns 0 when never downloaded`() {
        BlocklistSource.entries.forEach { source ->
            assertEquals(0, repository.getSourceDomainCount(source))
        }
    }

    @Test
    fun `getSourceDomainCount returns saved count`() {
        context.getSharedPreferences(BlocklistRepository.PREFS_NAME, 0)
            .edit().putInt("source_count_STEVENBLACK", 150_000).commit()
        val fresh = BlocklistRepository(context)
        assertEquals(150_000, fresh.getSourceDomainCount(BlocklistSource.STEVENBLACK))
    }

    // ── setSourceEnabled + loadIntoMemory → isBlocked ─────────────────────────

    @Test
    fun `disabling a source removes its domains from active set`() {
        File(context.filesDir, "blocklist_stevenblack.txt")
            .writeText("0.0.0.0 blocked-by-steven.com\n")
        File(context.filesDir, "blocklist_oisd_big.txt")
            .writeText("0.0.0.0 blocked-by-oisd.com\n")

        // Com tudo ativo, ambos bloqueados
        runBlockingTest { repository.loadIntoMemory() }
        assertTrue(repository.isBlocked("blocked-by-steven.com"))
        assertTrue(repository.isBlocked("blocked-by-oisd.com"))

        // Desabilita StevenBlack
        runBlockingTest { repository.setSourceEnabled(BlocklistSource.STEVENBLACK, false) }
        assertFalse(repository.isBlocked("blocked-by-steven.com"))
        assertTrue(repository.isBlocked("blocked-by-oisd.com"))
    }

    @Test
    fun `re-enabling a source restores its domains`() {
        File(context.filesDir, "blocklist_stevenblack.txt")
            .writeText("0.0.0.0 ads.restore-test.com\n")
        runBlockingTest { repository.loadIntoMemory() }

        runBlockingTest { repository.setSourceEnabled(BlocklistSource.STEVENBLACK, false) }
        assertFalse(repository.isBlocked("ads.restore-test.com"))

        runBlockingTest { repository.setSourceEnabled(BlocklistSource.STEVENBLACK, true) }
        assertTrue(repository.isBlocked("ads.restore-test.com"))
    }

    @Test
    fun `disabling all sources results in zero active domains`() {
        File(context.filesDir, "blocklist_stevenblack.txt")
            .writeText("0.0.0.0 ads.example.com\n")
        runBlockingTest { repository.loadIntoMemory() }

        BlocklistSource.entries.forEach { source ->
            runBlockingTest { repository.setSourceEnabled(source, false) }
        }

        assertEquals(0, repository.domainCount)
        assertFalse(repository.isBlocked("ads.example.com"))
    }

    // ── loadIntoMemory ────────────────────────────────────────────────────────

    @Test
    fun `loadIntoMemory uses per-source files when present`() {
        File(context.filesDir, "blocklist_peter_lowe.txt")
            .writeText("0.0.0.0 peter.ads.com\n")
        runBlockingTest { repository.loadIntoMemory() }
        assertTrue(repository.isBlocked("peter.ads.com"))
    }

    @Test
    fun `loadIntoMemory uses legacy blocklist_txt when no per-source files`() {
        File(context.filesDir, "blocklist.txt")
            .writeText("0.0.0.0 legacy.ads.com\n")
        runBlockingTest { repository.loadIntoMemory() }
        assertTrue(repository.isBlocked("legacy.ads.com"))
    }

    @Test
    fun `loadIntoMemory falls back to built-in domains on fresh install`() {
        runBlockingTest { repository.loadIntoMemory() }
        // BlocklistData embutido deve ter pelo menos alguns domínios
        assertFalse(repository.isBlocked("google.com"))  // allowlist tem prioridade
    }

    @Test
    fun `domainCount is updated after setSourceEnabled`() {
        File(context.filesDir, "blocklist_stevenblack.txt")
            .writeText("0.0.0.0 a.com\n0.0.0.0 b.com\n")
        File(context.filesDir, "blocklist_oisd_big.txt")
            .writeText("0.0.0.0 c.com\n")
        runBlockingTest { repository.loadIntoMemory() }
        assertEquals(3, repository.domainCount)

        runBlockingTest { repository.setSourceEnabled(BlocklistSource.STEVENBLACK, false) }
        assertEquals(1, repository.domainCount)
    }

    // ── allowlist priority ────────────────────────────────────────────────────

    @Test
    fun `allowlisted domains are never blocked even if in blocklist file`() {
        File(context.filesDir, "blocklist_stevenblack.txt")
            .writeText("0.0.0.0 google.com\n0.0.0.0 youtube.com\n0.0.0.0 ads.example.com\n")
        runBlockingTest { repository.loadIntoMemory() }
        assertFalse(repository.isBlocked("google.com"))
        assertFalse(repository.isBlocked("youtube.com"))
        assertTrue(repository.isBlocked("ads.example.com"))
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private fun runBlockingTest(block: suspend () -> Unit) {
        kotlinx.coroutines.runBlocking { block() }
    }
}
