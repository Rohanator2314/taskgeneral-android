# Changelog

All notable changes to this project will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] — 2026-03-14

Initial public release.

### Added

#### Core (Rust / TaskChampion)
- Full task CRUD via TaskChampion 3.0.1 — create, read, update, delete, complete
- `start_task` / `stop_task` — mark a task active or return it to pending
- Due date, wait date, and recurrence fields on tasks (stored as RFC 3339 strings)
- Urgency scoring using the Taskwarrior formula:
  - Coefficients: `next` tag (15), overdue (12), priority H (6), active (4), priority M (3.9), age (2), priority L (1.8), tags (1), project (1), waiting (−3)
- `list_tasks_sorted` — server-side sort by urgency, due date, priority, creation date, or description
- Waiting task auto-hide — waiting tasks are excluded from the pending filter until their wait date passes
- UniFFI exports: `start_task`, `stop_task`, `list_tasks_sorted`, `SortField`
- 47 Rust unit tests covering all new functionality

#### Sync
- Sync with self-hosted [taskchampion-sync-server](https://github.com/GothenburgBitFactory/taskchampion-sync-server)
- End-to-end encryption of sync data (encryption secret stored in `EncryptedSharedPreferences`)
- Manual sync via "Sync Now" button
- Background sync via WorkManager (periodic, 15-minute minimum interval, network-constrained)
- Sync status feedback (success / error message in settings screen)

#### Android UI (Kotlin / Jetpack Compose)
- Task list screen with urgency-sorted tasks by default
- Filter row: filter by status (pending, active, waiting, completed), project, and tags
- Sort dropdown: sort by urgency, due date, priority, creation date, or description
- Task create / edit screen:
  - Due date picker (Material 3 `DatePicker`)
  - Wait date picker
  - Recurrence field (daily, weekly, monthly, yearly) — display only, no auto-instance generation
  - Start / Stop button with active-state visual feedback
  - Tag input with comma-delimited or Enter-to-confirm entry; space-separated words create separate tags
- Task list item indicators: due date (red when overdue), ACTIVE badge, WAITING badge, recurrence symbol
- App settings screen: configure default sort order and urgency weights per coefficient
- Sync settings screen: server URL, client ID, encryption secret, sync now, last-sync timestamp
- Sharp-corner design language throughout (`RectangleShape`) with purple primary theme

#### Infrastructure
- GitHub Actions CI pipeline: Rust tests (fmt + clippy + test), Android debug build, Android unit tests
- Apache 2.0 license
- Architecture Decision Record: [ADR 001 — UniFFI and Kotlin/Compose](docs/adr/001-architecture-uniffi-kotlin.md)

---

<!-- Links -->
[1.0.0]: https://github.com/Rohanator2314/taskgeneral-android/releases/tag/v1.0.0
