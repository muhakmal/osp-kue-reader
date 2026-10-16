# Agent Guidelines & Context: KUE Reader SDK

This repository is **KUE Reader SDK**, an open-source native Android library (SDK) and toolkit written in Kotlin that reads Indonesian contactless electronic money cards (Mandiri e-money, BCA Flazz, BNI TapCash, BRI Brizzi, JakCard, and KMT) using Android NFC (IsoDep & FeliCa).

---

## 🎯 Architecture & Tech Stack

- **Library Module**: `:kue-reader` (Android Library AAR, minSdk 24, compileSdk 36)
- **Sample Module**: `:sample` (Minimal Jetpack Compose reference app)
- **Language**: Kotlin 2.0.21
- **NFC Engine**: Android `android.nfc` API with custom `ApduChannel` abstraction
- **Build System**: Gradle Kotlin DSL (`build.gradle.kts`, `gradle/libs.versions.toml`)
- **Distribution**: JitPack / Maven (`maven-publish`)

---

## 🔒 Security & Privacy Non-Negotiables

1. **Strictly Read-Only NFC**: Never implement APDUs that attempt to write to contactless cards or compromise cryptographic modules.
2. **Offline-First & Zero Telemetry**: Card parsing, cardholder balances, and transaction logs must NEVER be transmitted across network sockets. The library module requires zero internet permissions.
3. **No Hardcoded Secrets**: Do not commit keystores, passwords, private keys, or API tokens.
4. **Conventional Commits**: All commits must follow Conventional Commits (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `chore:`).

---

## 🛠️ Verification Commands

```bash
make test        # Runs: ./gradlew :kue-reader:testDebugUnitTest
make build       # Runs: ./gradlew :kue-reader:assembleRelease :sample:assembleDebug
make lint        # Runs: ./gradlew :kue-reader:lint
```
Always ensure unit tests pass with zero regressions before proposing code changes.
