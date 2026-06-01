# Contributing to ADSBanish

Thank you for considering a contribution. All help is welcome — bug reports, feature suggestions, code improvements, and documentation fixes.

---

## `> REPORTING BUGS`

Open an [Issue](https://github.com/rbracini/adsbanish/issues) and include:

- Android version and device model
- App version (`v${BuildConfig.VERSION_NAME}` shown in the shield)
- Steps to reproduce
- Expected vs. actual behavior
- Logcat output if available (`adb logcat -s ADSBanish`)

---

## `> SUGGESTING FEATURES`

Open an Issue with the `enhancement` label. Describe:

- The problem you're trying to solve
- Your proposed solution
- Any alternatives you considered

---

## `> SUBMITTING CODE`

### 1. Fork & clone

```bash
git clone https://github.com/YOUR_USERNAME/adsbanish.git
cd adsbanish
```

### 2. Create a branch

```bash
git checkout -b feat/your-feature-name
# or
git checkout -b fix/issue-description
```

### 3. Make your changes

- Follow the existing code style (Kotlin idiomatic, Compose conventions)
- Keep the single-screen architecture — no navigation library
- No new HTTP dependencies — use `HttpURLConnection` only
- Run lint before submitting: `./gradlew lint`

### 4. Test

```bash
./gradlew assembleDebug          # must build without errors
./gradlew lint                   # must pass
```

### 5. Commit

Use clear, concise commit messages:

```
feat: add custom blocklist URL support
fix: prevent VPN crash on rapid toggle
refactor: simplify DnsPacket parser
```

### 6. Open a Pull Request

- Target branch: `main`
- Describe **what** changed and **why**
- Reference related issues with `Closes #123`

---

## `> CODE GUIDELINES`

| Rule | Detail |
|---|---|
| Language | Kotlin only |
| UI | Jetpack Compose — no XML layouts |
| Async | Kotlin Coroutines — no RxJava |
| Networking | `HttpURLConnection` — no Retrofit, no OkHttp |
| Architecture | Single Activity, `AndroidViewModel`, `StateFlow` |
| Min SDK | 26 — no APIs below Android 8.0 |

---

## `> LICENSE`

By contributing, you agree that your code will be licensed under the [MIT License](./LICENSE).
