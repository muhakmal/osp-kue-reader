# Contributing to OSP KUE Reader

Thank you for your interest in contributing to **OSP KUE Reader**! We welcome community contributions, whether it is adding support for a new contactless card variant, optimizing APDU parsing, fixing bugs, or improving documentation.

---

## 🛠️ Prerequisites & Setup

1. **JDK 17 or 21**
2. **Android SDK** (API Level 36, Build Tools installed)
3. **Android Studio** (Koala / Ladybug or newer recommended)

### Quick Start
```bash
# 1. Clone repository
git clone https://github.com/muhakmal/osp-kue-reader.git
cd osp-kue-reader

# 2. Run unit test suite
./gradlew testDebugUnitTest
# or with Makefile:
make test

# 3. Assemble debug APK
./gradlew assembleDebug
# or with Makefile:
make build
```

The APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

---

## 🌿 Git & Branch Workflow

1. Always branch from `main`:
   ```bash
   git checkout main
   git pull origin main
   git checkout -b <type>/<issue-number>-<short-description>
   ```
   *Allowed types*: `feat`, `fix`, `chore`, `docs`, `refactor`, `test`, `ci`  
   *Example*: `feat/12-add-jaklingko-parser` or `fix/45-tapcash-date-overflow`

2. Follow **Conventional Commits**:
   - `feat: add APDU parser for JakLingko card`
   - `fix: resolve BCD parsing error for legacy Mandiri logs`
   - `docs: update card compatibility matrix in README`
   - `test: add unit tests for Felica Suica transit parser`

3. When opening a Pull Request:
   - Fill out the provided [PR Template](.github/PULL_REQUEST_TEMPLATE.md).
   - Reference the related issue: `Closes #12`.
   - Ensure all unit tests pass locally before submitting: `make test`.

---

## 💳 Adding a New Card Parser

If you are adding support for a new contactless card (or a new firmware/applet revision):

1. **Create the Parser class** under `app/src/main/java/com/baskom/pembacakue/nfc/parser/`:
   ```kotlin
   package com.baskom.pembacakue.nfc.parser

   import android.nfc.Tag
   import com.baskom.pembacakue.CardData

   class MyNewCardParser : EmoneyParser {
       override fun canHandle(tag: Tag): Boolean {
           // Check techList (e.g. IsoDep, NfcF, etc.)
       }

       override fun parse(tag: Tag, logBuilder: StringBuilder): CardData? {
           // Transceive APDUs via ApduChannel, parse balance & transactions
       }
   }
   ```
2. **Register the Parser** in `app/src/main/java/com/baskom/pembacakue/CardParser.kt` in the parser registry list.
3. **Write Unit Tests** in `app/src/test/java/com/baskom/pembacakue/`:
   - Mock APDU responses using mock channels or byte arrays.
   - Test both success and failure cases.
4. **Card Data Privacy**:
   - Ensure **NO personal data or full card credentials** are logged.
   - Use `TxDates` utilities for formatting timestamps.

---

## 🧪 Code Style & Guidelines

- **Architecture**: Kotlin + Jetpack Compose + MVVM / Unidirectional Data Flow (MVI).
- **Offline First**: All card parsing, transaction history, and toll estimations run 100% on-device. No telemetry or network calls should be added to card reading pathways.
- **Formatting**: Adhere to official Kotlin style guidelines.

---

## 📜 Legal & Compliance Guidelines

- **Read-Only APDU Commands Only**: Under no circumstances will PRs containing write commands, balance altering APDUs, SAM key extractors, or card cloning algorithms be accepted.
- All contributions are licensed under the [Apache License 2.0](LICENSE).
