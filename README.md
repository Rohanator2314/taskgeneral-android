# TaskGeneral

An open-source Android client for Taskwarrior 3.x, built with Rust core and Kotlin/Jetpack Compose UI.

## Overview

TaskGeneral is an offline-first task management app that syncs with self-hosted [taskchampion-sync-server](https://github.com/GothenburgBitFactory/taskchampion-sync-server). The app combines the power of Rust's TaskChampion library for task logic with a native Android Material Design 3 interface.

**Key Features:**
- ✅ Full task CRUD (create, read, update, delete, complete)
- 📅 Due dates, wait dates, and recurrence fields
- ⚡ Urgency scoring — Taskwarrior-compatible formula with automatic sort
- ▶️ Start / stop tasks with active-task tracking
- 🔍 Filter by status (pending, active, waiting, completed), project, and tags
- 🔃 Sort by urgency, due date, priority, creation date, or description
- 🔄 Sync with taskchampion-sync-server (manual and automatic background sync)
- 📱 Offline-first with local SQLite storage
- ⚙️ App settings — configure default sort order and urgency weights
- 🔐 Encrypted sync settings (EncryptedSharedPreferences)

## Architecture

TaskGeneral uses a **Rust core + Kotlin UI** architecture:

- **Rust core** (`rust/taskgeneral-core`): Wraps [TaskChampion 3.0.1](https://docs.rs/taskchampion/3.0.1) library for task storage, filtering, and sync operations
- **UniFFI bridge**: Automatically generates Kotlin bindings from Rust code using [Mozilla UniFFI](https://mozilla.github.io/uniffi-rs/)
- **Android app** (`app/`): Kotlin/Jetpack Compose UI with Material Design 3

**Why this architecture?** See [ADR 001: Architecture Decision - UniFFI and Kotlin/Compose](docs/adr/001-architecture-uniffi-kotlin.md) for the rationale behind choosing UniFFI+Kotlin over full Rust UI frameworks (Slint, Dioxus) or Flutter.

## Prerequisites

- **Android Studio** Ladybug (2024.2.1) or later
- **Android SDK** with API 26+ (Android 8.0 Oreo minimum, API 35 target)
- **Rust** 1.88.0 or later (MSRV for TaskChampion)
- **Android NDK** (installed via Android Studio SDK Manager)
- **Java 17** (for Gradle build)

### Install Rust Android Targets

```bash
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android
```

## Build Instructions

### 1. Clone the repository

```bash
git clone --recursive https://github.com/Rohanator2314/taskgeneral-android.git
cd taskgeneral-android
```

> **Note:** The `--recursive` flag initialises the `rust/taskgeneral-core` submodule automatically.
> If you already cloned without it, run: `git submodule update --init`

### 2. Build Rust core (optional - Gradle does this automatically)

```bash
cd rust/taskgeneral-core
cargo build
cargo test
cd ../..
```

### 3. Build Android app

```bash
./gradlew assembleDebug
```

The APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

### 4. Run tests

**Rust tests:**
```bash
cd rust/taskgeneral-core && cargo test
```

**Android unit tests:**
```bash
./gradlew test
```

**Linting:**
```bash
# Rust
cd rust/taskgeneral-core && cargo clippy -- -D warnings

# Android (Note: lint task may be slow)
./gradlew lint
```

## Running on Emulator

### 1. Start an Android emulator (API 26+)

In Android Studio: **Tools → Device Manager → Create/Start Virtual Device**

Or via command line:
```bash
emulator -avd <your-avd-name>
```

### 2. Install the app

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or run directly from Android Studio (click **Run** ▶️).

### 3. Set up sync (optional)

To test sync functionality, run the taskchampion-sync-server:

```bash
docker run -d -p 8080:8080 ghcr.io/gothenburgbitfactory/taskchampion-sync-server
```

Then in the app:
1. Tap the **settings icon** (⚙️) in the top bar
2. Enter server URL: `http://10.0.2.2:8080` (10.0.2.2 is the emulator's host machine)
3. Enter an encryption secret (any string)
4. Tap **Save**
5. Return to task list and tap **Sync** button

**Note:** For production use, deploy taskchampion-sync-server on a server with HTTPS.

## Project Structure

```
.
├── app/                          # Android app module
│   ├── src/main/java/dev/rohans/taskwarrior/
│   │   ├── MainActivity.kt       # Main activity with navigation
│   │   ├── MainApplication.kt    # Application class
│   │   ├── data/
│   │   │   └── TaskRepository.kt # Kotlin wrapper around Rust core
│   │   ├── navigation/
│   │   │   └── Route.kt          # Navigation routes
│   │   ├── ui/
│   │   │   ├── screens/          # Task list, edit, sync settings screens
│   │   │   ├── components/       # Reusable UI components
│   │   │   └── theme/            # Material Design 3 theme
│   │   └── viewmodel/
│   │       └── TaskViewModel.kt  # ViewModel for task operations
│   ├── build.gradle.kts          # Android build config + UniFFI/Cargo plugins
│   └── proguard-rules.pro        # ProGuard rules for UniFFI
├── rust/taskgeneral-core/        # Rust core library
│   ├── src/
│   │   ├── lib.rs                # UniFFI scaffolding
│   │   ├── task_manager.rs       # TaskManager wrapping TaskChampion
│   │   ├── models.rs             # Data models (TaskInfo, TaskFilter, etc.)
│   │   └── error.rs              # Error types
│   └── Cargo.toml                # Rust dependencies
├── docs/adr/                     # Architecture Decision Records
│   └── 001-architecture-uniffi-kotlin.md
├── build.gradle.kts              # Root build config
├── settings.gradle.kts           # Gradle settings
└── README.md                     # This file
```

## Development Workflow

1. **Make Rust changes:** Edit files in `rust/taskgeneral-core/src/`, run `cargo test`
2. **Make Android changes:** Edit files in `app/src/`, run `./gradlew assembleDebug`
3. **Sync Rust → Android:** UniFFI bindings regenerate automatically during `./gradlew assembleDebug`
4. **Test:** Run `cargo test` for Rust, `./gradlew test` for Android unit tests
5. **Lint:** Run `cargo clippy` and `./gradlew lint` before committing

## License

Apache 2.0 License

See [LICENSE](LICENSE) for details.

## Contributing

Contributions are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for setup instructions, code style, and the pull request process.

## Architecture Documentation

For a deep dive into the architecture decision (why UniFFI + Kotlin/Compose instead of full Rust UI frameworks), see:

📄 [ADR 001: Architecture Decision - UniFFI and Kotlin/Compose](docs/adr/001-architecture-uniffi-kotlin.md)

## Acknowledgments

- [TaskChampion](https://crates.io/crates/taskchampion) — Rust library for Taskwarrior 3.x
- [Mozilla UniFFI](https://mozilla.github.io/uniffi-rs/) — Rust ↔ Kotlin FFI bindings
- [Gobley Gradle Plugins](https://github.com/ArcticLampyrid/gobley) — Cargo + UniFFI integration for Gradle
- [Taskwarrior](https://taskwarrior.org/) — Task management ecosystem
