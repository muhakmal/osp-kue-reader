# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.x     | :white_check_mark: |
| < 1.0   | :x:                |

## Reporting a Vulnerability

We take the security of OSP KUE Reader and the privacy of its users seriously.

If you believe you have discovered a security vulnerability (such as a potential data leak, improper NFC communication handling, or security flaw in cryptographic parsing), please follow these responsible disclosure steps:

1. **Do NOT open a public GitHub issue.**
2. Send an email to the project maintainer: **muhakmal@gmail.com** or open a [GitHub Private Vulnerability Report](https://github.com/muhakmal/osp-kue-reader/security/advisories/new).
3. Include detailed reproduction steps, device model, Android OS version, and relevant logs if available.
4. Allow up to 48 hours for an initial response.

### Scope & Privacy Guarantees
- OSP KUE Reader processes contactless card information **strictly on-device**.
- No card identifiers (UID/PAN), transaction records, or financial balances are sent over the network.
- The app only issues standard read APDU commands (`SELECT`, `READ RECORD`, `READ BINARY`) without attempting write operations or cryptographic tampering.
