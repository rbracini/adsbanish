# Especificação v2 — App Android: ADSBanish

> Atualização da v1: adiciona download de blocklist online, botão "Atualizar Lista",
> contador de domínios, data da última atualização e seleção de fonte.

---

## 1. Visão Geral

| Item | Valor |
|------|-------|
| Nome do app | **ADSBanish** |
| Package | `br.com.adsbanish` |
| Linguagem | Kotlin |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 |
| UI toolkit | Jetpack Compose |
| Persistência | Arquivo interno (`filesDir/blocklist.txt`) + SharedPreferences para metadados |
| Sincronismo | Manual — somente quando usuário toca "Atualizar Lista" |
| Telas | **Uma única tela** |

---

## 2. Dependências — `build.gradle.kts (app)`

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "br.com.adsbanish"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.adsbanish"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0.0"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-service:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    // Sem Retrofit, sem OkHttp — download via HttpURLConnection nativo
}
```

---

## 3. AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>

    <application
        android:name=".App"
        android:label="ADSBanish"
        android:icon="@mipmap/ic_launcher"
        android:theme="@style/Theme.ADSBanish"
        android:allowBackup="false">

        <activity
            android:name=".ui.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>

        <service
            android:name=".vpn.AdBlockVpnService"
            android:permission="android.permission.BIND_VPN_SERVICE"
            android:foregroundServiceType="specialUse"
            android:exported="false">
            <intent-filter>
                <action android:name="android.net.VpnService"/>
            </intent-filter>
            <property
                android:name="android.net.VpnService.SPECIAL_USE_DESCRIPTION"
                android:value="Bloqueia anúncios de todos os aplicativos via DNS local"/>
        </service>

    </application>
</manifest>
```

---

## 4. Estrutura de Arquivos

```
app/src/main/
├── java/br/com/adsbanish/
│   ├── App.kt
│   ├── blocklist/
│   │   ├── BlocklistSource.kt        ← NOVO: enum das fontes disponíveis
│   │   ├── BlocklistRepository.kt    ← NOVO: download + arquivo local + metadados
│   │   └── BlocklistData.kt          ← fallback hardcoded (sem alteração da v1)
│   ├── vpn/
│   │   ├── AdBlockVpnService.kt      ← atualizado: carrega do repositório
│   │   ├── PacketProcessor.kt        ← sem alteração
│   │   ├── DnsPacket.kt              ← sem alteração
│   │   └── VpnState.kt               ← sem alteração
│   └── ui/
│       ├── MainActivity.kt           ← atualizado: injeta ViewModel
│       ├── MainViewModel.kt          ← NOVO: estado do download
│       ├── MainScreen.kt             ← atualizado: novos elementos de UI
│       └── theme/
│           ├── Color.kt
│           ├── Theme.kt
│           └── Type.kt
└── res/
    └── drawable/
        └── ic_shield.xml
```

---

## 5. Fontes de Blocklist Disponíveis

### 5.1 `blocklist/BlocklistSource.kt`

Enum com as fontes públicas suportadas. Cada fonte tem nome exibido na UI,
URL de download e formato do arquivo.

```kotlin
package br.com.adsbanish.blocklist

enum class BlocklistSource(
    val displayName: String,
    val description: String,
    val url: String,
    val format: BlocklistFormat
) {
    STEVENBLACK(
        displayName = "StevenBlack",
        description = "~150k domínios — Ads + Malware + Tracking",
        url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
        format = BlocklistFormat.HOSTS
    ),
    OISD_SMALL(
        displayName = "OISD Small",
        description = "~50k domínios — Ads + Tracking (leve)",
        url = "https://small.oisd.nl/",
        format = BlocklistFormat.HOSTS
    ),
    OISD_BIG(
        displayName = "OISD Big",
        description = "~300k domínios — Ads + Tracking + Malware (completo)",
        url = "https://big.oisd.nl/",
        format = BlocklistFormat.HOSTS
    ),
    PETER_LOWE(
        displayName = "Peter Lowe",
        description = "~3k domínios — Foco em Ads (minimalista)",
        url = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0",
        format = BlocklistFormat.HOSTS
    ),
    ADGUARD(
        displayName = "AdGuard DNS",
        description = "~50k domínios — Curado pelo AdGuard",
        url = "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt",
        format = BlocklistFormat.ADBLOCK
    );
}

enum class BlocklistFormat {
    /**
     * Formato hosts padrão:
     *   0.0.0.0 ads.example.com
     *   127.0.0.1 tracker.example.com
     *   # comentário
     * Extrai a segunda coluna de linhas não-comentário que começam com 0.0.0.0 ou 127.0.0.1
     */
    HOSTS,

    /**
     * Formato AdBlock/AdGuard:
     *   ||ads.example.com^
     *   ! comentário
     * Extrai o domínio entre || e ^
     */
    ADBLOCK
}
```

---

### 5.2 `blocklist/BlocklistRepository.kt`

Responsável por:
- Baixar a lista da fonte selecionada via `HttpURLConnection`.
- Parsear o formato correto (`HOSTS` ou `ADBLOCK`).
- Salvar o resultado em `filesDir/blocklist.txt` (armazenamento interno do app, sem permissão necessária).
- Persistir metadados em `SharedPreferences`: data da última atualização, contagem de domínios, fonte selecionada.
- Expor o `Set<String>` em memória para o `PacketProcessor`.
- Nunca fazer download automático — somente quando chamado explicitamente.

```kotlin
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
        private const val KEY_SOURCE       = "selected_source"
        private const val BLOCKLIST_FILE   = "blocklist.txt"
        private const val TIMEOUT_MS       = 30_000
        val DATE_FORMAT = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val blocklistFile: File = File(context.filesDir, BLOCKLIST_FILE)

    // Set em memória — carregado uma vez, consultado milhares de vezes por segundo
    private var _domains: Set<String> = emptySet()
    val domains: Set<String> get() = _domains

    // ── Metadados ─────────────────────────────────────────────────────────────

    val lastUpdateTimestamp: Long
        get() = prefs.getLong(KEY_LAST_UPDATE, 0L)

    val lastUpdateFormatted: String
        get() = if (lastUpdateTimestamp == 0L) "Nunca"
                else DATE_FORMAT.format(Date(lastUpdateTimestamp))

    val domainCount: Int
        get() = prefs.getInt(KEY_DOMAIN_COUNT, 0)

    var selectedSource: BlocklistSource
        get() = BlocklistSource.valueOf(
            prefs.getString(KEY_SOURCE, BlocklistSource.OISD_SMALL.name)!!
        )
        set(value) = prefs.edit().putString(KEY_SOURCE, value.name).apply()

    val hasLocalBlocklist: Boolean get() = blocklistFile.exists()

    // ── Inicialização ─────────────────────────────────────────────────────────

    /**
     * Carrega o Set<String> em memória.
     * Prioridade: arquivo local → fallback hardcoded.
     * Deve ser chamado no Application.onCreate() ou antes de iniciar o VpnService.
     */
    suspend fun loadIntoMemory() = withContext(Dispatchers.IO) {
        _domains = if (hasLocalBlocklist) {
            parseHostsFile(blocklistFile.readLines())
        } else {
            BlocklistData.BLOCKED_DOMAINS // fallback hardcoded
        }
    }

    // ── Download ──────────────────────────────────────────────────────────────

    /**
     * Baixa a blocklist da [source] selecionada, salva no arquivo interno
     * e recarrega o Set em memória.
     *
     * Emite progresso via [onProgress] (0..100).
     * Lança exceção em caso de falha de rede ou HTTP != 200.
     * Deve ser chamado em Dispatchers.IO.
     */
    suspend fun downloadAndUpdate(
        source: BlocklistSource = selectedSource,
        onProgress: (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        onProgress(0)

        val connection = (URL(source.url).openConnection() as HttpURLConnection).apply {
            requestMethod    = "GET"
            connectTimeout   = TIMEOUT_MS
            readTimeout      = TIMEOUT_MS
            setRequestProperty("User-Agent", "ADSBanish-Android/2.0")
        }

        try {
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP ${connection.responseCode}: ${connection.responseMessage}")
            }

            onProgress(10)

            // Stream para arquivo temporário primeiro (evita corromper o arquivo atual em caso de falha)
            val tempFile = File(context.filesDir, "blocklist_tmp.txt")
            val contentLength = connection.contentLength.toLong()
            var bytesRead = 0L

            connection.inputStream.bufferedReader().use { reader ->
                tempFile.bufferedWriter().use { writer ->
                    reader.forEachLine { line ->
                        writer.write(line)
                        writer.newLine()
                        bytesRead += line.length + 1
                        if (contentLength > 0) {
                            val progress = 10 + ((bytesRead * 85) / contentLength).toInt()
                            onProgress(progress.coerceAtMost(95))
                        }
                    }
                }
            }

            onProgress(95)

            // Parseia e valida antes de substituir o arquivo atual
            val lines = tempFile.readLines()
            val parsed = when (source.format) {
                BlocklistFormat.HOSTS   -> parseHostsFile(lines)
                BlocklistFormat.ADBLOCK -> parseAdblockFile(lines)
            }

            if (parsed.isEmpty()) throw Exception("Lista vazia ou formato inválido")

            // Substitui arquivo atual pelo temporário
            tempFile.copyTo(blocklistFile, overwrite = true)
            tempFile.delete()

            // Atualiza metadados
            prefs.edit()
                .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                .putInt(KEY_DOMAIN_COUNT, parsed.size)
                .putString(KEY_SOURCE, source.name)
                .apply()

            // Recarrega em memória
            _domains = parsed
            selectedSource = source

            onProgress(100)

        } finally {
            connection.disconnect()
        }
    }

    // ── Parsers ───────────────────────────────────────────────────────────────

    /**
     * Parseia formato hosts:
     *   0.0.0.0 ads.example.com   → extrai "ads.example.com"
     *   127.0.0.1 tracker.com     → extrai "tracker.com"
     *   # comentário              → ignora
     *   localhost                 → ignora (linha sem domínio real)
     */
    private fun parseHostsFile(lines: List<String>): Set<String> {
        val result = HashSet<String>(lines.size / 2)
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith('#')) continue
            val parts = trimmed.split("\\s+".toRegex())
            if (parts.size < 2) continue
            val ip = parts[0]
            if (ip != "0.0.0.0" && ip != "127.0.0.1") continue
            val domain = parts[1].lowercase()
            if (domain == "localhost" || domain == "broadcasthost") continue
            result.add(domain)
        }
        return result
    }

    /**
     * Parseia formato AdBlock/AdGuard:
     *   ||ads.example.com^        → extrai "ads.example.com"
     *   ! comentário              → ignora
     *   # comentário              → ignora
     */
    private fun parseAdblockFile(lines: List<String>): Set<String> {
        val result = HashSet<String>(lines.size / 2)
        val regex = Regex("""^\|\|([a-zA-Z0-9.\-]+)\^""")
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith('!') || trimmed.startsWith('#')) continue
            regex.find(trimmed)?.groupValues?.getOrNull(1)?.lowercase()?.let {
                result.add(it)
            }
        }
        return result
    }

    // ── Consulta ──────────────────────────────────────────────────────────────

    /**
     * Verifica se o domínio (ou qualquer domínio pai) está bloqueado.
     * Consulta o Set em memória — O(1) por lookup.
     */
    fun isBlocked(domain: String): Boolean {
        if (_domains.isEmpty()) return BlocklistData.isBlocked(domain)
        var current = domain.lowercase().trimEnd('.')
        while (current.contains('.')) {
            if (_domains.contains(current)) return true
            current = current.substringAfter('.')
        }
        return false
    }
}
```

---

### 5.3 `App.kt`

Inicializa o repositório na criação da Application e expõe como singleton.

```kotlin
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
        // Carrega a blocklist em memória ao iniciar o app
        scope.launch { repository.loadIntoMemory() }
    }
}
```

---

### 5.4 `ui/MainViewModel.kt`

Gerencia o estado da tela: download em andamento, progresso, erros e metadados da lista.

```kotlin
package br.com.adsbanish.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.adsbanish.App
import br.com.adsbanish.blocklist.BlocklistSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class DownloadState {
    object Idle : DownloadState()
    data class Loading(val progress: Int) : DownloadState()
    object Success : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as App).repository

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState

    private val _domainCount = MutableStateFlow(repository.domainCount)
    val domainCount: StateFlow<Int> = _domainCount

    private val _lastUpdate = MutableStateFlow(repository.lastUpdateFormatted)
    val lastUpdate: StateFlow<String> = _lastUpdate

    private val _selectedSource = MutableStateFlow(repository.selectedSource)
    val selectedSource: StateFlow<BlocklistSource> = _selectedSource

    fun selectSource(source: BlocklistSource) {
        _selectedSource.value = source
        repository.selectedSource = source
    }

    fun updateBlocklist() {
        if (_downloadState.value is DownloadState.Loading) return
        viewModelScope.launch {
            try {
                _downloadState.value = DownloadState.Loading(0)
                repository.downloadAndUpdate(
                    source = _selectedSource.value,
                    onProgress = { progress ->
                        _downloadState.value = DownloadState.Loading(progress)
                    }
                )
                _domainCount.value = repository.domainCount
                _lastUpdate.value = repository.lastUpdateFormatted
                _downloadState.value = DownloadState.Success
            } catch (e: Exception) {
                _downloadState.value = DownloadState.Error(e.message ?: "Erro desconhecido")
            }
        }
    }

    fun dismissError() {
        _downloadState.value = DownloadState.Idle
    }
}
```

---

### 5.5 `vpn/AdBlockVpnService.kt`

Agora usa `BlocklistRepository` (via `App`) em vez do `BlocklistData` diretamente.
O `PacketProcessor` recebe o repositório para consultar `isBlocked()`.

```kotlin
package br.com.adsbanish.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import androidx.core.app.NotificationCompat
import br.com.adsbanish.App
import br.com.adsbanish.R
import br.com.adsbanish.blocklist.BlocklistRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AdBlockVpnService : VpnService() {

    companion object {
        private const val CHANNEL_ID      = "adblocker_vpn"
        private const val NOTIFICATION_ID = 1

        private val _state = MutableStateFlow<VpnState>(VpnState.Inactive)
        val state: StateFlow<VpnState> = _state
    }

    private var scope        = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var tunInterface : android.os.ParcelFileDescriptor? = null
    private lateinit var repository: BlocklistRepository

    override fun onCreate() {
        super.onCreate()
        repository = (application as App).repository
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        try {
            val tun = Builder()
                .setSession("ADSBanish")
                .addAddress("10.10.0.1", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("10.10.0.1")
                .setMtu(1500)
                .setBlocking(true)
                .establish() ?: throw IllegalStateException("Falha ao criar interface VPN")

            tunInterface = tun
            _state.value = VpnState.Active

            scope.launch {
                // Passa o repositório para o processador consultar isBlocked()
                PacketProcessor(
                    vpnService  = this@AdBlockVpnService,
                    tunFd       = tun.fileDescriptor,
                    isBlocked   = { domain -> repository.isBlocked(domain) }
                ).run()
            }

        } catch (e: Exception) {
            _state.value = VpnState.Error(e.message ?: "Erro desconhecido")
            stopSelf()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        tunInterface?.close()
        tunInterface = null
        _state.value = VpnState.Inactive
        super.onDestroy()
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ADSBanish ativo")
            .setContentText("Bloqueando ${repository.domainCount.takeIf { it > 0 }?.let { "$it domínios" } ?: "anúncios"}")
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
```

---

### 5.6 `vpn/PacketProcessor.kt`

Mesma implementação da v1, mas agora recebe `isBlocked` como lambda
para desacoplar do `BlocklistData` hardcoded.

```kotlin
package br.com.adsbanish.vpn

import android.net.VpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

class PacketProcessor(
    private val vpnService: VpnService,
    private val tunFd: FileDescriptor,
    private val isBlocked: (String) -> Boolean   // ← injetado, não acoplado
) {
    private val upstreamDns = InetAddress.getByName("1.1.1.1")
    private val dnsPort = 53

    suspend fun run() = withContext(Dispatchers.IO) {
        val input  = FileInputStream(tunFd)
        val output = FileOutputStream(tunFd)
        val buffer = ByteArray(32767)

        while (isActive) {
            val length = try { input.read(buffer) } catch (e: Exception) { break }
            if (length <= 0) continue
            processIpPacket(ByteBuffer.wrap(buffer, 0, length), length, output)
        }
    }

    private fun processIpPacket(packet: ByteBuffer, length: Int, output: FileOutputStream) {
        val firstByte     = packet.get(0).toInt() and 0xFF
        val ipVersion     = firstByte shr 4
        if (ipVersion != 4) { forwardRaw(packet.array(), length, output); return }

        val ipHeaderLength = (firstByte and 0x0F) * 4
        val protocol       = packet.get(9).toInt() and 0xFF
        if (protocol != 17) { forwardRaw(packet.array(), length, output); return }

        val udpOffset = ipHeaderLength
        val dstPort   = (packet.get(udpOffset + 2).toInt() and 0xFF shl 8) or
                        (packet.get(udpOffset + 3).toInt() and 0xFF)
        val srcPort   = (packet.get(udpOffset).toInt()     and 0xFF shl 8) or
                        (packet.get(udpOffset + 1).toInt() and 0xFF)

        if (dstPort != dnsPort) { forwardRaw(packet.array(), length, output); return }

        val udpPayloadOffset = udpOffset + 8
        val udpPayloadLength = length - udpPayloadOffset
        if (udpPayloadLength <= 0) return

        val dnsData   = packet.array().copyOfRange(udpPayloadOffset, udpPayloadOffset + udpPayloadLength)
        val dnsPacket = DnsPacket.parse(dnsData, udpPayloadLength) ?: return

        if (isBlocked(dnsPacket.questionName)) {
            val nxResp = DnsPacket.buildNxdomainResponse(dnsPacket)
            writeUdpResponse(packet.array().copyOfRange(0, ipHeaderLength), dnsPort, srcPort, nxResp, output)
        } else {
            forwardDnsQuery(dnsData, udpPayloadLength, srcPort, packet.array(), ipHeaderLength, output)
        }
    }

    private fun forwardDnsQuery(
        dnsPayload: ByteArray, length: Int, originalSrcPort: Int,
        ipHeader: ByteArray, ipHeaderLength: Int, output: FileOutputStream
    ) {
        try {
            val socket = DatagramSocket()
            vpnService.protect(socket)
            socket.soTimeout = 3000
            socket.send(DatagramPacket(dnsPayload, length, upstreamDns, dnsPort))
            val buf     = ByteArray(4096)
            val receive = DatagramPacket(buf, buf.size)
            socket.receive(receive)
            socket.close()
            writeUdpResponse(ipHeader.copyOfRange(0, ipHeaderLength), dnsPort, originalSrcPort, buf.copyOf(receive.length), output)
        } catch (_: Exception) {}
    }

    private fun writeUdpResponse(
        ipHeader: ByteArray, udpSrcPort: Int, udpDstPort: Int,
        payload: ByteArray, output: FileOutputStream
    ) {
        val udpLength   = 8 + payload.size
        val totalLength = ipHeader.size + udpLength
        val response    = ByteBuffer.allocate(totalLength)
        val modifiedIp  = ipHeader.copyOf()

        System.arraycopy(ipHeader, 16, modifiedIp, 12, 4)
        System.arraycopy(ipHeader, 12, modifiedIp, 16, 4)
        modifiedIp[2] = (totalLength shr 8).toByte()
        modifiedIp[3] = (totalLength and 0xFF).toByte()
        modifiedIp[10] = 0; modifiedIp[11] = 0
        val cs = checksum(modifiedIp, 0, ipHeader.size)
        modifiedIp[10] = (cs shr 8).toByte()
        modifiedIp[11] = (cs and 0xFF).toByte()

        response.put(modifiedIp)
        response.putShort(udpSrcPort.toShort())
        response.putShort(udpDstPort.toShort())
        response.putShort(udpLength.toShort())
        response.putShort(0)
        response.put(payload)

        output.write(response.array(), 0, totalLength)
    }

    private fun forwardRaw(data: ByteArray, length: Int, output: FileOutputStream) {
        try { output.write(data, 0, length) } catch (_: Exception) {}
    }

    private fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0; var i = offset
        while (i < offset + length - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF); i += 2
        }
        if ((length and 1) != 0) sum += (data[offset + length - 1].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }
}
```

---

### 5.7 `ui/MainActivity.kt`

```kotlin
package br.com.adsbanish.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
                    vpnState      = AdBlockVpnService.state,
                    viewModel     = viewModel,
                    onVpnToggle   = ::handleVpnToggle
                )
            }
        }
    }

    private fun handleVpnToggle(currentState: VpnState) {
        when (currentState) {
            is VpnState.Active -> stopService(Intent(this, AdBlockVpnService::class.java))
            else -> {
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
```

---

### 5.8 `ui/MainScreen.kt`

Tela única com todos os elementos. Layout do topo para baixo:

```
┌─────────────────────────────────────┐
│          ADSBanish                  │  ← título leve
│                                     │
│         [🛡 animado]                │  ← escudo com pulso quando ativo
│                                     │
│       Proteção Ativa                │  ← status (verde/cinza)
│    152.847 domínios bloqueados      │  ← contador (some se 0)
│                                     │
│       [ ATIVAR / DESATIVAR ]        │  ← botão principal
│                                     │
│  ┌──── Fonte da lista ────────────┐ │
│  │  ○ StevenBlack    ~150k        │ │  ← seletor de fonte
│  │  ● OISD Small     ~50k  ✓     │ │
│  │  ○ OISD Big       ~300k       │ │
│  │  ○ Peter Lowe     ~3k         │ │
│  │  ○ AdGuard DNS    ~50k        │ │
│  └───────────────────────────────┘ │
│                                     │
│    [ Atualizar Lista ]              │  ← botão secundário
│   Última atualização: 14/05 18:32  │  ← metadata
│                                     │
│    ████████░░░░ 73%  Baixando...   │  ← progress bar (só durante download)
└─────────────────────────────────────┘
```

```kotlin
package br.com.adsbanish.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.adsbanish.blocklist.BlocklistSource
import br.com.adsbanish.vpn.VpnState
import kotlinx.coroutines.flow.StateFlow
import java.text.NumberFormat
import java.util.Locale

@Composable
fun MainScreen(
    vpnState    : StateFlow<VpnState>,
    viewModel   : MainViewModel,
    onVpnToggle : (VpnState) -> Unit
) {
    val state          by vpnState.collectAsState()
    val downloadState  by viewModel.downloadState.collectAsState()
    val domainCount    by viewModel.domainCount.collectAsState()
    val lastUpdate     by viewModel.lastUpdate.collectAsState()
    val selectedSource by viewModel.selectedSource.collectAsState()

    val isActive    = state is VpnState.Active
    val isDownloading = downloadState is DownloadState.Loading

    // Animação de pulso no escudo
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = if (isActive) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val shieldColor by animateColorAsState(
        targetValue    = if (isActive) Color(0xFF00E676) else Color(0xFF455A64),
        animationSpec  = tween(600), label = "shieldColor"
    )
    val buttonColor by animateColorAsState(
        targetValue   = if (isActive) Color(0xFFD32F2F) else Color(0xFF00C853),
        animationSpec = tween(400), label = "buttonColor"
    )

    // Snackbar de erro
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(downloadState) {
        if (downloadState is DownloadState.Error) {
            snackbarHostState.showSnackbar(
                message     = (downloadState as DownloadState.Error).message,
                actionLabel = "OK"
            )
            viewModel.dismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF0D0D0D)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(32.dp))

            // ── Título ────────────────────────────────────────────────────
            Text(
                text         = "ADSBanish",
                fontSize     = 28.sp,
                fontWeight   = FontWeight.Light,
                color        = Color.White,
                letterSpacing = 4.sp
            )

            // ── Escudo ────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .scale(pulseScale)
                    .background(shieldColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint     = shieldColor,
                    modifier = Modifier.size(88.dp)
                )
            }

            // ── Status ────────────────────────────────────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text       = when (state) {
                        is VpnState.Active -> "Proteção Ativa"
                        is VpnState.Error  -> "Erro na VPN"
                        else               -> "Proteção Inativa"
                    },
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (isActive) Color(0xFF00E676) else Color(0xFF90A4AE)
                )
                if (domainCount > 0) {
                    Text(
                        text      = "${NumberFormat.getNumberInstance(Locale("pt", "BR")).format(domainCount)} domínios bloqueados",
                        fontSize  = 13.sp,
                        color     = Color(0xFF546E7A),
                        modifier  = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // ── Botão VPN ─────────────────────────────────────────────────
            Button(
                onClick  = { onVpnToggle(state) },
                modifier = Modifier.width(220.dp).height(52.dp),
                shape    = RoundedCornerShape(26.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = buttonColor),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                Text(
                    text          = if (isActive) "DESATIVAR" else "ATIVAR",
                    fontSize      = 15.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color         = Color.White
                )
            }

            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = Color(0xFF1E1E1E))

            // ── Seletor de fonte ──────────────────────────────────────────
            Text(
                text       = "Fonte da lista",
                fontSize   = 12.sp,
                fontWeight = FontWeight.Medium,
                color      = Color(0xFF546E7A),
                letterSpacing = 1.sp,
                modifier   = Modifier.fillMaxWidth()
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111111), RoundedCornerShape(12.dp))
                    .padding(vertical = 4.dp)
            ) {
                BlocklistSource.entries.forEach { source ->
                    val isSelected = selectedSource == source
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                onClick  = { if (!isDownloading) viewModel.selectSource(source) },
                                role     = Role.RadioButton
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick  = null,
                            colors   = RadioButtonDefaults.colors(
                                selectedColor   = Color(0xFF00E676),
                                unselectedColor = Color(0xFF455A64)
                            )
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text       = source.displayName,
                                fontSize   = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color      = if (isSelected) Color.White else Color(0xFF90A4AE)
                            )
                            Text(
                                text     = source.description,
                                fontSize = 11.sp,
                                color    = Color(0xFF455A64)
                            )
                        }
                    }
                }
            }

            // ── Botão Atualizar ───────────────────────────────────────────
            OutlinedButton(
                onClick  = { viewModel.updateBlocklist() },
                enabled  = !isDownloading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.outlinedButtonColors(
                    contentColor        = Color(0xFF00E676),
                    disabledContentColor = Color(0xFF455A64)
                )
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier  = Modifier.size(18.dp),
                        color     = Color(0xFF455A64),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Baixando...", fontSize = 14.sp)
                } else {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Atualizar Lista", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }

            // ── Barra de progresso ────────────────────────────────────────
            AnimatedVisibility(visible = isDownloading) {
                val progress = (downloadState as? DownloadState.Loading)?.progress ?: 0
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LinearProgressIndicator(
                        progress       = { progress / 100f },
                        modifier       = Modifier.fillMaxWidth().height(4.dp),
                        color          = Color(0xFF00E676),
                        trackColor     = Color(0xFF1E1E1E),
                        strokeCap      = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text      = "$progress%",
                        fontSize  = 11.sp,
                        color     = Color(0xFF546E7A),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // ── Metadados ─────────────────────────────────────────────────
            Text(
                text      = "Última atualização: $lastUpdate",
                fontSize  = 12.sp,
                color     = Color(0xFF37474F),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}
```

---

### 5.9 Arquivos sem alteração da v1

Os arquivos abaixo são **idênticos à v1** — copiar sem modificação:

- `vpn/VpnState.kt`
- `vpn/DnsPacket.kt`
- `blocklist/BlocklistData.kt` (fallback hardcoded)
- `ui/theme/Color.kt`
- `ui/theme/Theme.kt`
- `res/drawable/ic_shield.xml`

---

## 6. Fluxo Completo

```
Primeira abertura
    → App.onCreate() → repository.loadIntoMemory()
    → blocklistFile não existe → carrega BlocklistData hardcoded (~150 domínios)
    → domainCount = 0 → UI não exibe contador

Usuário toca "Atualizar Lista" (ex: OISD Small)
    → MainViewModel.updateBlocklist()
    → GET https://small.oisd.nl/
    → Progress: 0% → 10% → ... → 95% → 100%
    → parseia ~50k domínios
    → salva em filesDir/blocklist.txt
    → prefs: last_update=agora, domain_count=50000, source=OISD_SMALL
    → _domains em memória = novo Set de 50k domínios
    → UI: "50.000 domínios bloqueados" / "Última atualização: 15/05 10:32"

Usuário toca "ATIVAR"
    → diálogo VPN do Android → confirma
    → AdBlockVpnService inicia
    → PacketProcessor lê tunInterface em loop
    → app abre YouTube → query DNS para pagead2.googlesyndication.com
    → isBlocked("pagead2.googlesyndication.com") = true
    → responde NXDOMAIN → anúncio não carrega

Usuário troca fonte para "OISD Big" e atualiza novamente
    → _domains substituído por ~300k domínios em memória
    → VPN continua rodando sem interrupção (consulta sempre o _domains atual)
```

---

## 7. Armazenamento — resumo

| O que | Onde | Tecnologia |
|-------|------|------------|
| Domínios bloqueados | `filesDir/blocklist.txt` | `File` (texto puro) |
| Data última atualização | SharedPreferences | `getLong(KEY_LAST_UPDATE)` |
| Contagem de domínios | SharedPreferences | `getInt(KEY_DOMAIN_COUNT)` |
| Fonte selecionada | SharedPreferences | `getString(KEY_SOURCE)` |
| Set em memória | RAM | `HashSet<String>` |

---

## 8. Comando para o Claude Code

```
Implemente um aplicativo Android nativo Kotlin seguindo estritamente 
o arquivo SPEC_AdsBanish.md.

Crie todos os arquivos listados na seção 4. Os arquivos marcados como
"sem alteração da v1" devem ser copiados da especificação anterior
(SPEC_AdsBanish.md seção 5).

Não adicione dependências além das listadas na seção 2.
Não crie telas adicionais.
Siga os nomes de pacotes, classes e métodos exatamente como especificado.
```
