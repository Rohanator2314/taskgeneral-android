# Security Policy

## Supported versions

| Version | Security fixes |
|---------|---------------|
| 1.x     | ✅ Yes         |

## Reporting a vulnerability

**Do not open a public GitHub issue for security vulnerabilities.**

Report vulnerabilities privately via [GitHub Security Advisories](https://github.com/Rohanator2314/taskwarrior-android/security/advisories/new), or by emailing the maintainers directly (see the repository's contact information).

Include in your report:
- A description of the vulnerability and its potential impact
- Steps to reproduce or a proof-of-concept
- Affected versions
- Any suggested mitigations, if known

You will receive an acknowledgment within 72 hours. Once the issue is confirmed and a fix is prepared, a coordinated disclosure timeline will be agreed upon before anything is made public.

## Scope

This policy covers:
- The TaskGeneral Android application (`app/`)
- The Rust core library (`rust/taskgeneral-core/`)

This policy does **not** cover:
- The upstream [TaskChampion](https://github.com/GothenburgBitFactory/taskchampion) library — report those upstream
- The upstream [taskchampion-sync-server](https://github.com/GothenburgBitFactory/taskchampion-sync-server) — report those upstream
- Self-hosted sync server deployments operated by users

## Security considerations for users

- **Encryption secret**: The sync encryption secret is stored in Android `EncryptedSharedPreferences` backed by the Android Keystore. It is not transmitted in plaintext. Use a strong, unique secret.
- **Sync server**: TaskGeneral supports HTTPS sync servers. For production use, deploy `taskchampion-sync-server` with TLS. HTTP sync should only be used on a local network during development.
- **Task data**: Task data is stored in a local SQLite database managed by TaskChampion. The database file is located in the app's private data directory and is not accessible to other apps on a non-rooted device.
