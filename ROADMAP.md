# 🗺️ KUE Reader SDK Roadmap

This roadmap outlines planned technical milestones and card protocol additions for **KUE Reader SDK**.

---

## 📌 Status Legend
- 🟢 **Completed**: Available in the current release.
- 🟡 **In Progress**: Under active development or testing.
- 🔵 **Planned**: Backlog priorities for upcoming versions.
- 🟣 **Under Research**: Protocol reverse-engineering or awaiting hardware test cards.

---

## 🚀 Milestones & SDK Evolution

### Milestone 1: Core Engine & Multi-Card Support 🟢
- [x] Decoupled Android Library (`kue-reader`) module with zero bloat.
- [x] Pluggable `EmoneyParser` interface and `ApduChannel` abstraction.
- [x] Full balance and history parsing for:
  - Mandiri e-money (IsoDep `00 D1` and legacy `00 B2`).
  - BCA Flazz Gen 2.
  - BNI TapCash.
  - BRI Brizzi (DESFire wrapped commands).
  - Bank DKI JakCard.
  - KMT (FeliCa / NFC-F transit format).
- [x] Offline unit test suite with mock APDU responses.
- [x] Sample Jetpack Compose application (`:sample`).
- [x] Apache 2.0 license and GitHub Actions CI.

---

### Milestone 2: Ecosystem & Developer Experience (Q4 2026) 🟡
- [ ] **JakLingko Transit Card Parser** (Issue #2):
  - Add native DESFire EV1/EV2 parser for Jakarta integrated transit cards.
- [ ] **JitPack & Maven Central Publishing**:
  - Automated release pipeline publishing AAR and source JARs to Maven Central.
- [ ] **Extended APDU Diagnostic Tracer**:
  - Export low-level APDU interaction logs to standard PCAP or CSV format for community debugging.

---

### Milestone 3: Cross-Platform & Kotlin Multiplatform (2027) 🔵
- [ ] **Kotlin Multiplatform (KMP) Core**:
  - Extract pure Kotlin parser algorithms into a KMP module supporting both Android and JVM (desktop PC/SC card readers like ACR122U).
- [ ] **Extended Regional Contactless Cards**:
  - Research Singapore EZ-Link and NETS FlashPay read-only balance formats.
  - Research Malaysia Touch 'n Go NFC card protocol compatibility.
