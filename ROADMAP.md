# 🗺️ OSP KUE Reader Roadmap

This roadmap outlines the planned development milestones, requested card protocols, and long-term goals for **OSP KUE Reader**.

---

## 📌 Status Legend
- 🟢 **Completed**: Shipped in the current release.
- 🟡 **In Progress**: Under active development or testing.
- 🔵 **Planned**: Backlog priority for upcoming minor releases.
- 🟣 **Under Research**: Exploring feasibility, reverse-engineering protocols, or awaiting hardware test cards.

---

## 🚀 Milestones & Phases

### Phase 1: Foundation & Core Card Support 🟢
- [x] Native Jetpack Compose Material 3 interface.
- [x] Offline IsoDep & FeliCa NFC reading engine (`ApduChannel`).
- [x] Full balance & transaction history support for:
  - Mandiri e-money (new 00 D1 format and legacy formats).
  - BCA Flazz Gen 2.
  - BNI TapCash.
  - BRI Brizzi.
  - Bank DKI JakCard.
  - KMT (Kartu Multi Trip - Commuter Line & MRT).
- [x] Voice announcements engine for Rupiah balances (Indonesian language voice packs).
- [x] Local Room SQLite database for scan logs and expense categorization.
- [x] Integrated offline Indonesian toll expressway fare calculator.
- [x] Open-source project setup with Apache 2.0 license, clean-room sanitization, and GitHub Actions CI.

---

### Phase 2: Community Expansion & Card Ecosystem (Q4 2026) 🟡
- [ ] **JakLingko Card Support** (Issue #2):
  - Dedicated ISO/IEC 14443-4 APDU parser for Jakarta integrated transit cards.
  - Multi-operator trip history deduction detection (Transjakarta, MRT, LRT, Microtrans).
- [ ] **Material You Dynamic Theming** (Issue #3):
  - Dynamic palette extraction from Android 12+ wallpaper.
  - OLED true dark mode enhancement.
- [ ] **Enhanced Transaction Exporting**:
  - Export local scan history to CSV and JSON formats for personal budgeting (e.g., Wallet, Spendee, Excel).
- [ ] **Extended Haptic Feedback**:
  - Custom vibrational cues for successful read vs unsupported/misaligned card.

---

### Phase 3: Hardware Diagnostics & NFC Hardening (Q1 2027) 🔵
- [ ] **NFC Signal Diagnostics Mode**:
  - Visual NFC antenna sweet-spot finder to assist users in locating their phone's NFC coil.
  - Low-level APDU response timing metrics to debug slow cards.
- [ ] **F-Droid Release & Reproducible Builds** (Issue #4):
  - Reproducible APK compilation pipeline.
  - Submission to F-Droid and IzzyOnDroid repositories for independent distribution.
- [ ] **Extended Transit Cards Research**:
  - Singapore EZ-Link / NETS FlashPay (read-only balance).
  - Malaysia Touch 'n Go (NFC variant compatibility investigation).

---

### Phase 4: Developer SDK & Ecosystem (2027+) 🟣
- [ ] **Decoupled NFC Core Library (`kue-reader-core`)**:
  - Publish an independent Kotlin Multiplatform (KMP) or Android AAR library for the APDU card parsers.
  - Allow other Indonesian developers to embed e-money balance checking in their open-source apps.

---

## 🤝 How to Influence the Roadmap
Have a suggestion or request?
- Open a [Feature Request](https://github.com/muhakmal/osp-kue-reader/issues/new?template=feature_request.yml).
- Request support for a new card via [Card Support Request](https://github.com/muhakmal/osp-kue-reader/issues/new?template=card_support.yml).
- Discuss in [Discussions](https://github.com/muhakmal/osp-kue-reader/discussions).
