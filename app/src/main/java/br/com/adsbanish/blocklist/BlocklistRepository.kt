package br.com.adsbanish.blocklist

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class BlocklistRepository(private val context: Context) {

    companion object {
        private const val PREFS_NAME       = "adblocker_prefs"
        private const val KEY_LAST_UPDATE  = "last_update_ts"
        private const val KEY_DOMAIN_COUNT = "domain_count"
        private const val BLOCKLIST_FILE   = "blocklist.txt"
        private const val TIMEOUT_MS       = 30_000
        val DATE_FORMAT = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        // Domínios sempre permitidos — têm prioridade sobre qualquer lista de bloqueio
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
        )
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val blocklistFile: File = File(context.filesDir, BLOCKLIST_FILE)

    private var _domains: Set<String> = emptySet()
    val domains: Set<String> get() = _domains

    val lastUpdateTimestamp: Long
        get() = prefs.getLong(KEY_LAST_UPDATE, 0L)

    val lastUpdateFormatted: String
        get() = if (lastUpdateTimestamp == 0L) "Nunca"
                else DATE_FORMAT.format(Date(lastUpdateTimestamp))

    val domainCount: Int
        get() = prefs.getInt(KEY_DOMAIN_COUNT, 0)

    val hasLocalBlocklist: Boolean get() = blocklistFile.exists()

    suspend fun loadIntoMemory() = withContext(Dispatchers.IO) {
        _domains = if (hasLocalBlocklist) {
            parseHostsFile(blocklistFile.readLines())
        } else {
            BlocklistData.BLOCKED_DOMAINS
        }
    }

    /**
     * Baixa todas as fontes em sequência e mescla os domínios.
     * Falhas individuais são ignoradas — continua com as demais fontes.
     * [onProgress] 0..100 para o progresso geral.
     * [onStatus] nome da fonte sendo baixada no momento.
     */
    suspend fun downloadAllAndUpdate(
        onProgress: (Int) -> Unit = {},
        onStatus: (String) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val allDomains = HashSet<String>()
        val sources = BlocklistSource.entries
        var successCount = 0

        sources.forEachIndexed { index, source ->
            val startPct = (index * 100) / sources.size
            val endPct   = ((index + 1) * 100) / sources.size

            onStatus("${source.displayName} (${index + 1}/${sources.size})")
            onProgress(startPct)

            try {
                val domains = downloadSource(source) { innerProgress ->
                    onProgress(startPct + (innerProgress * (endPct - startPct)) / 100)
                }
                allDomains.addAll(domains)
                successCount++
            } catch (_: Exception) {
                // continua com a próxima fonte
            }
        }

        if (allDomains.isEmpty())
            throw Exception("Nenhuma lista pôde ser baixada. Verifique sua conexão.")

        // Salva conjunto mesclado em formato hosts
        val tempFile = File(context.filesDir, "blocklist_tmp.txt")
        tempFile.bufferedWriter().use { writer ->
            for (domain in allDomains) {
                writer.write("0.0.0.0 $domain")
                writer.newLine()
            }
        }
        tempFile.copyTo(blocklistFile, overwrite = true)
        tempFile.delete()

        prefs.edit()
            .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
            .putInt(KEY_DOMAIN_COUNT, allDomains.size)
            .apply()

        _domains = allDomains
        onProgress(100)
    }

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
        if (_domains.isEmpty()) return BlocklistData.isBlocked(domain)
        return BlocklistParser.isBlocked(domain, _domains, ALLOWLIST)
    }

}
