package br.com.adsbanish.blocklist

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class BlocklistRepository(private val context: Context) {

    companion object {
        const val PREFS_NAME                 = "adblocker_prefs"
        private const val KEY_LAST_UPDATE    = "last_update_ts"
        private const val KEY_DOMAIN_COUNT   = "domain_count"
        private const val KEY_SOURCE_ENABLED = "source_enabled_"
        private const val KEY_SOURCE_COUNT   = "source_count_"
        private const val BLOCKLIST_FILE     = "blocklist.txt"
        private const val TIMEOUT_MS         = 30_000
        private val dateFormatter: DateTimeFormatter = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.systemDefault())

        val ALLOWLIST: Set<String> = setOf(
            // YouTube
            "youtube.com", "youtu.be", "googlevideo.com", "ytimg.com",
            "yt3.ggpht.com", "youtubei.googleapis.com", "ggpht.com",
            "youtube-nocookie.com",
            // Google core
            "google.com", "google.com.br", "googleapis.com", "gstatic.com",
            "googleusercontent.com", "android.com",
            "firebaseapp.com", "firebase.google.com",
            "play.google.com", "accounts.google.com",
            // Apple
            "apple.com", "icloud.com", "mzstatic.com",
            // Microsoft / Email
            "microsoft.com", "microsoftonline.com", "outlook.com", "live.com",
            "hotmail.com", "office.com", "windows.com",
            // Meta / WhatsApp
            "whatsapp.com", "whatsapp.net", "facebook.com", "instagram.com",
            "fbcdn.net", "cdninstagram.com",
            // Streaming / entretenimento
            "netflix.com", "nflxvideo.net", "nflximg.com",
            "spotify.com", "scdn.co", "spotifycdn.com",
            "twitch.tv", "twitchsvc.net", "jtvnw.net",
            "tiktok.com", "byteoversea.com",
            "reddit.com", "redd.it", "redditmedia.com", "reddituploads.com",
            // Bancos e pagamentos BR
            "itau.com.br", "bradesco.com.br", "bb.com.br", "caixa.gov.br",
            "nubank.com.br", "mercadopago.com.br", "mercadolivre.com.br",
            "picpay.com", "pagseguro.com.br",
            // Governo / serviços BR
            "gov.br", "receita.fazenda.gov.br", "correios.com.br",
            // CDN e infraestrutura legítima
            "cloudflare.com", "cloudflare-dns.com",
            "fastly.net", "akamai.net", "akamaized.net", "akamaihd.net",
            "edgekey.net", "edgesuite.net",
            "amazonaws.com", "awsstatic.com",
            "azureedge.net", "azure.com",
            // Comunicação
            "telegram.org", "t.me",
            "zoom.us", "zoom.com",
            "discord.com", "discordapp.com",
            // Outros apps populares
            "uber.com", "ifood.com.br", "rappi.com.br",
            "airbnb.com", "booking.com",
            "wikipedia.org", "wikimedia.org",
            "github.com", "githubusercontent.com",
            // Redirect de e-mail marketing legítimo (HubSpot, Mailchimp, SendGrid…)
            // Bloquear esses domínios impede abrir links em newsletters
            "hubspotlinks.com", "hs-analytics.net",
            "list-manage.com", "mailchi.mp",
            "sendgrid.net", "sendgrid.com",
            "click.convertkit-mail.com", "convertkit.com",
        )
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val blocklistFile: File = File(context.filesDir, BLOCKLIST_FILE)

    @Volatile private var _domains: Set<String> = emptySet()

    // Cache por fonte — permite rebuild sem I/O de disco ao togglear fontes
    @Volatile private var perSourceDomains: Map<BlocklistSource, Set<String>> = emptyMap()

    val activeDomainCount: Int get() = _domains.size

    val lastUpdateTimestamp: Long get() = prefs.getLong(KEY_LAST_UPDATE, 0L)

    val lastUpdateFormatted: String
        get() = if (lastUpdateTimestamp == 0L) "Nunca"
                else dateFormatter.format(Instant.ofEpochMilli(lastUpdateTimestamp))

    val domainCount: Int get() = prefs.getInt(KEY_DOMAIN_COUNT, 0)

    val hasLocalBlocklist: Boolean get() = blocklistFile.exists()

    val hasDownloadedList: Boolean
        get() = domainCount > 0 || hasLocalBlocklist || hasAnySourceFile()

    // ── Per-source helpers ────────────────────────────────────────────────────

    private fun sourceFile(source: BlocklistSource) =
        File(context.filesDir, "blocklist_${source.name.lowercase()}.txt")

    private fun hasAnySourceFile() =
        BlocklistSource.entries.any { sourceFile(it).exists() }

    fun isSourceEnabled(source: BlocklistSource): Boolean =
        prefs.getBoolean(KEY_SOURCE_ENABLED + source.name, true)

    fun getSourceDomainCount(source: BlocklistSource): Int =
        prefs.getInt(KEY_SOURCE_COUNT + source.name, 0)

    suspend fun setSourceEnabled(source: BlocklistSource, enabled: Boolean) =
        withContext(Dispatchers.IO) {
            prefs.edit().putBoolean(KEY_SOURCE_ENABLED + source.name, enabled).apply()
            rebuildActiveDomains()
        }

    // ── Core loading ──────────────────────────────────────────────────────────

    /**
     * Mescla apenas as fontes habilitadas a partir do cache em memória (sem I/O).
     * Quando o cache está vazio (pré-download), recai no BlocklistData embutido.
     */
    private suspend fun rebuildActiveDomains() = withContext(Dispatchers.IO) {
        val cache = perSourceDomains
        val merged = HashSet<String>()
        for ((source, domains) in cache) {
            if (isSourceEnabled(source)) merged.addAll(domains)
        }
        _domains = when {
            merged.isNotEmpty() -> merged
            cache.isEmpty()     -> BlocklistData.BLOCKED_DOMAINS
            else                -> emptySet()
        }
        prefs.edit().putInt(KEY_DOMAIN_COUNT, _domains.size).apply()
    }

    suspend fun loadIntoMemory() = withContext(Dispatchers.IO) {
        when {
            hasAnySourceFile() -> {
                val cache = HashMap<BlocklistSource, Set<String>>()
                for (source in BlocklistSource.entries) {
                    val file = sourceFile(source)
                    if (file.exists() && file.length() > 0) {
                        cache[source] = parseHostsFile(file.readLines())
                    }
                }
                perSourceDomains = cache
                rebuildActiveDomains()
            }
            hasLocalBlocklist  -> {
                // Arquivo legado de versão anterior — usado só até o primeiro update
                _domains = parseHostsFile(blocklistFile.readLines())
                prefs.edit().putInt(KEY_DOMAIN_COUNT, _domains.size).apply()
            }
            else -> _domains = BlocklistData.BLOCKED_DOMAINS
        }
    }

    // ── Download ──────────────────────────────────────────────────────────────

    /**
     * Baixa todas as fontes e salva cada uma em arquivo próprio (substituição, nunca acúmulo).
     * Ao final reconstrói o conjunto ativo a partir das fontes habilitadas.
     */
    suspend fun downloadAllAndUpdate(
        onProgress: (Int) -> Unit = {},
        onStatus: (String) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val sources = BlocklistSource.entries
        var successCount = 0

        sources.forEachIndexed { index, source ->
            val startPct = (index * 100) / sources.size
            val endPct   = ((index + 1) * 100) / sources.size

            onStatus("${source.displayName} (${index + 1}/${sources.size})")
            onProgress(startPct)

            try {
                val domains = downloadSource(source) { inner ->
                    onProgress(startPct + (inner * (endPct - startPct)) / 100)
                }
                if (domains.size < 100)
                    throw Exception("${source.displayName}: lista com dados insuficientes (${domains.size} domínios)")
                val file = sourceFile(source)
                val tmp  = File(context.filesDir, "blocklist_${source.name.lowercase()}_tmp.txt")
                try {
                    tmp.bufferedWriter().use { w ->
                        for (d in domains) { w.write("0.0.0.0 $d"); w.newLine() }
                    }
                    if (!tmp.renameTo(file)) {
                        tmp.copyTo(file, overwrite = true)
                    }
                } finally {
                    if (tmp.exists()) tmp.delete()
                }
                perSourceDomains = perSourceDomains + (source to domains)
                prefs.edit().putInt(KEY_SOURCE_COUNT + source.name, domains.size).apply()
                successCount++
            } catch (_: Exception) {
                // mantém arquivo da fonte já existente se o download falhar
            }
        }

        if (successCount == 0 && !hasAnySourceFile())
            throw Exception("Nenhuma lista pôde ser baixada. Verifique sua conexão.")

        rebuildActiveDomains()
        if (successCount == sources.size) {
            prefs.edit().putLong(KEY_LAST_UPDATE, System.currentTimeMillis()).apply()
        }
        onProgress(100)
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private suspend fun downloadSource(
        source: BlocklistSource,
        onProgress: (Int) -> Unit
    ): Set<String> = withContext(Dispatchers.IO) {
        onProgress(0)
        val connection = (URL(source.url).openConnection() as HttpURLConnection).apply {
            requestMethod  = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout    = TIMEOUT_MS
            setRequestProperty("User-Agent", "ADSBanish-Android/2.0")
        }
        try {
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK)
                throw Exception("HTTP ${connection.responseCode}")
            onProgress(20)
            val lines = connection.inputStream.bufferedReader().readLines()
            onProgress(80)
            val result = when (source.format) {
                BlocklistFormat.HOSTS   -> parseHostsFile(lines)
                BlocklistFormat.ADBLOCK -> parseAdblockFile(lines)
            }
            onProgress(100)
            result
        } finally {
            connection.disconnect()
        }
    }

    private fun parseHostsFile(lines: List<String>): Set<String> =
        BlocklistParser.parseHostsFile(lines)

    private fun parseAdblockFile(lines: List<String>): Set<String> =
        BlocklistParser.parseAdblockFile(lines)

    fun isBlocked(domain: String): Boolean {
        val list = if (_domains.isEmpty()) BlocklistData.BLOCKED_DOMAINS else _domains
        return BlocklistParser.isBlocked(domain, list, ALLOWLIST)
    }
}
