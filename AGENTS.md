# Agent Guidelines & Context: OSP KUE Reader

This repository is **OSP KUE Reader**, a native Android open-source application written in Kotlin and Jetpack Compose that reads Indonesian contactless electronic money cards (Mandiri e-money, BCA Flazz, BNI TapCash, BRI Brizzi, JakCard, and KMT) using Android NFC (IsoDep & FeliCa).

---

## 🎯 Architecture & Tech Stack

- **Platform**: Android (minSdk 24, targetSdk 36, compileSdk 36)
- **Language**: Kotlin 2.0.21
- **UI Toolkit**: Jetpack Compose (Material 3)
- **Local Persistence**: Room Database (SQLite) + Jetpack DataStore / SharedPreferences
- **NFC Engine**: Android `android.nfc` API with custom `ApduChannel` abstraction
- **Build System**: Gradle Kotlin DSL (`build.gradle.kts`, `gradle/libs.versions.toml`)

---

## 🔒 Security & Privacy Non-Negotiables

1. **Strictly Read-Only NFC**: Never implement APDUs that attempt to write to contactless cards or compromise cryptographic modules.
2. **Offline-First & Zero Telemetry**: Card parsing, cardholder balances, and transaction logs must NEVER be transmitted across network sockets.
3. **No Hardcoded Secrets**: Do not commit keystores, passwords, private keys, or API tokens. Secrets must be supplied via environment variables or gitignored files.
4. **Conventional Commits**: All commits must follow Conventional Commits (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `chore:`).

---

## 🛠️ Verification Commands

```bash
make test        # or ./gradlew testDebugUnitTest
make build       # or ./gradlew assembleDebug
make lint        # or ./gradlew lintDebug
```
Always ensure unit tests pass with zero regressions before proposing code changes.
