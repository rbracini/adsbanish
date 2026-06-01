# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Android ad blocker app (`br.com.adsbanish`) that intercepts DNS queries via a local VPN and returns NXDOMAIN for blocked domains. Built with Kotlin + Jetpack Compose. **No code exists yet — this repo contains only a spec (`SPEC_AdsBanish.md`).**

## Build Commands

```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build + install on connected device/emulator
./gradlew test                   # unit tests
./gradlew connectedAndroidTest   # instrumented tests (requires device/emulator)
./gradlew lint                   # lint checks
```

## Architecture

Single-Activity app. No navigation library — one screen only.

```
App (Application)
 └── BlocklistRepository (singleton via lazy)
      ├── loads domains into memory at startup (filesDir/blocklist.txt → fallback to BlocklistData)
      └── downloadAndUpdate() — manual only, never automatic

MainActivity
 └── MainViewModel (AndroidViewModel)
      ├── DownloadState: Idle | Loading(progress) | Success | Error(message)
      └── calls repository.downloadAndUpdate() on user action

AdBlockVpnService (foreground service, VpnService)
 └── PacketProcessor
      ├── reads raw IP packets from TUN interface in a loop
      ├── intercepts UDP port 53 (DNS) only
      ├── calls isBlocked(domain) via lambda injected from repository
      └── returns NXDOMAIN or forwards to 1.1.1.1
```

**Data flow**: `App.onCreate()` → `repository.loadIntoMemory()` → VPN reads `repository.isBlocked()` on every DNS query (O(1) HashSet lookup). Updating the blocklist replaces `_domains` in memory; the running VPN picks up the new set immediately without restart.

## Key Implementation Rules

- **No Retrofit, no OkHttp** — downloads use `HttpURLConnection` only (see spec §2).
- **Two blocklist formats** to parse: `HOSTS` (`0.0.0.0 domain`) and `ADBLOCK` (`||domain^`).
- **Write to temp file first** during download, then `copyTo(blocklistFile, overwrite = true)` — never corrupt the current list on failure.
- **`isBlocked()`** walks up the domain hierarchy (checks `sub.example.com`, then `example.com`).
- **VPN state** is exposed as `AdBlockVpnService.state: StateFlow<VpnState>` companion object — UI collects it directly.
- **`BlocklistRepository`** is accessed via `(application as App).repository` from both `MainViewModel` and `AdBlockVpnService`.

## File Structure to Create

```
app/src/main/java/br/com/adsbanish/
├── App.kt
├── blocklist/
│   ├── BlocklistSource.kt     ← enum of 5 sources + BlocklistFormat enum
│   ├── BlocklistRepository.kt ← download, parse, persist, isBlocked()
│   └── BlocklistData.kt       ← hardcoded fallback domains (~150)
├── vpn/
│   ├── AdBlockVpnService.kt
│   ├── PacketProcessor.kt     ← raw packet I/O, DNS interception
│   ├── DnsPacket.kt           ← DNS parse + NXDOMAIN builder
│   └── VpnState.kt            ← sealed class: Inactive | Active | Error
└── ui/
    ├── MainActivity.kt
    ├── MainViewModel.kt
    ├── MainScreen.kt
    └── theme/
        ├── Color.kt
        ├── Theme.kt
        └── Type.kt
```

## SharedPreferences Keys

Stored in `"adblocker_prefs"`:

| Key | Type | Purpose |
|-----|------|---------|
| `last_update_ts` | Long | Epoch ms of last download |
| `domain_count` | Int | Number of domains in current list |
| `selected_source` | String | `BlocklistSource.name` |

## Required Permissions (AndroidManifest)

`INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`

The VPN service needs `android:permission="android.permission.BIND_VPN_SERVICE"` and `android:foregroundServiceType="specialUse"`.
