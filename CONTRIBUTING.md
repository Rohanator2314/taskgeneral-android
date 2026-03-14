# Contributing to TaskGeneral

Thanks for your interest in contributing. This document covers environment setup, code style, the test pipeline, and the pull request process.

## Table of Contents

- [Project overview](#project-overview)
- [Prerequisites](#prerequisites)
- [Environment setup](#environment-setup)
- [Project structure](#project-structure)
- [Development workflow](#development-workflow)
- [Code style](#code-style)
- [Testing](#testing)
- [Adding UniFFI-exposed functions](#adding-uniffi-exposed-functions)
- [Pull request process](#pull-request-process)
- [Common build issues](#common-build-issues)

---

## Project overview

TaskGeneral is split into two layers:

| Layer | Language | Location | Role |
|---|---|---|---|
| Core | Rust | `rust/taskgeneral-core/` | TaskChampion wrapper, business logic, data models |
| App | Kotlin | `app/` | Jetpack Compose UI, ViewModel, WorkManager sync |

The two layers communicate via [Mozilla UniFFI](https://mozilla.github.io/uniffi-rs/), which auto-generates Kotlin bindings from the Rust library at Gradle build time. You do not write or edit the generated bindings — they live in `app/build/` and are regenerated on every `./gradlew assembleDebug`.

---

## Prerequisites

| Tool | Version | Purpose |
|---|---|---|
| Rust | ≥ 1.88.0 | MSRV set by TaskChampion 3.0.1 |
| Android Studio | Ladybug (2024.2.1)+ | IDE and SDK manager |
| Android SDK | API 26 (min) / API 35 (target) | Build target |
| Android NDK | r26d | Cross-compile Rust for ARM |
| Java | 17 | Gradle |

Install Rust:
```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
```

Install Android cross-compilation targets:
```bash
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android
```

Install the NDK via Android Studio: **SDK Manager → SDK Tools → NDK (Side by side) → r26d**.

---

## Environment setup

```bash
# 1. Fork and clone
git clone https://github.com/your-org/taskwarrior-android.git
cd taskwarrior-android

# 2. Verify Rust builds and tests pass
cd rust/taskgeneral-core
cargo test
cd ../..

# 3. Verify Android builds (this also generates UniFFI bindings)
./gradlew assembleDebug
```

If `assembleDebug` succeeds, your environment is fully configured.

---

## Project structure

```
.
├── app/
│   └── src/main/java/dev/rohans/taskwarrior/
│       ├── MainActivity.kt           # Single-activity host, Compose NavHost
│       ├── MainApplication.kt        # WorkManager background sync scheduling
│       ├── data/
│       │   └── TaskRepository.kt     # All calls into the Rust core go here
│       ├── navigation/
│       │   └── Route.kt              # Sealed class of navigation routes
│       ├── ui/
│       │   ├── screens/              # Full-screen composables
│       │   ├── components/           # Reusable composables (TaskItem, FilterRow, …)
│       │   └── theme/                # Material 3 color scheme, typography
│       ├── viewmodel/
│       │   └── TaskViewModel.kt      # All UI state and user actions
│       └── work/
│           └── SyncWorker.kt         # Background sync via WorkManager
├── rust/taskgeneral-core/
│   └── src/
│       ├── lib.rs                    # UniFFI scaffolding — only public entry points
│       ├── task_manager.rs           # TaskManager: wraps TaskChampion, all core logic
│       ├── models.rs                 # TaskInfo, TaskUpdate, TaskFilter, SortField, …
│       └── error.rs                  # TaskError enum (thiserror)
├── docs/adr/                         # Architecture Decision Records
└── .github/workflows/ci.yml          # CI: rust-tests, android-build, android-tests
```

**Data flow:** UI → ViewModel → Repository → Rust (via UniFFI) → TaskChampion → SQLite

---

## Development workflow

### Rust-only change
```bash
cd rust/taskgeneral-core
# Edit src/task_manager.rs, models.rs, or error.rs
cargo test          # run all 47+ unit tests
cargo clippy -- -D warnings
cargo fmt
```

### Kotlin-only change
```bash
# Edit app/src/…
./gradlew assembleDebug   # compile check
./gradlew test            # unit tests
```

### Cross-layer change (new Rust function exposed to Kotlin)
See [Adding UniFFI-exposed functions](#adding-uniffi-exposed-functions).

### Bindings regeneration
UniFFI bindings regenerate automatically during `./gradlew assembleDebug`. You never need to run a separate binding generation step.

---

## Code style

### Rust
- Formatter: `rustfmt` (run `cargo fmt` before committing)
- Linter: Clippy with `-D warnings` (no allowed warnings)
- Patterns:
  - Use `Result<T>` with `?` for all fallible operations — no `unwrap()` on application code
  - Keep `lib.rs` thin — it contains only UniFFI entry point wrappers
  - Business logic belongs in `task_manager.rs`
  - Data shapes belong in `models.rs`
  - All new error variants go in `error.rs` using `thiserror`
  - tokio runtime: `new_current_thread()` only — the `rt-multi-thread` feature is not enabled

### Kotlin
- All UI composables use `RectangleShape` — no rounded corners anywhere in the UI
- No `!!` non-null assertions — use safe calls or explicit null checks
- No empty `catch` blocks — always log at minimum
- ViewModels use `viewModelScope` for coroutines
- No new third-party dependencies without discussion in an issue first
- New composable screens go in `ui/screens/`, reusable components in `ui/components/`

### Both
- Code should be self-documenting — avoid over-commenting obvious logic
- No excessive abstraction — keep patterns flat and direct
- No changes to the sync logic or `EncryptedSharedPreferences` without a linked issue

---

## Testing

### Rust unit tests
```bash
cd rust/taskgeneral-core
cargo test
```

Tests live at the bottom of `task_manager.rs` in a `#[cfg(test)] mod tests` block. Every new public function on `TaskManager` should have at least one test covering the happy path and one covering an error case.

### Android unit tests
```bash
./gradlew test
```

### Full CI pipeline (mirrors what GitHub Actions runs)
```bash
# Rust
cd rust/taskgeneral-core
cargo fmt --check
cargo clippy -- -D warnings
cargo test --verbose

# Android
./gradlew assembleDebug
./gradlew test
```

All CI checks must pass before a PR can be merged.

---

## Adding UniFFI-exposed functions

When you add new functionality that the Kotlin layer needs to call, follow these steps in order:

**1. Add the implementation in `task_manager.rs`**
```rust
// Follow the existing pattern: method on TaskManager, returns Result<T>
impl TaskManager {
    pub fn my_new_function(&self, param: String) -> Result<TaskInfo> {
        // ...
    }
}
```

**2. Add data types (if needed) in `models.rs`**
```rust
// Use #[derive(uniffi::Record)] for structs, #[derive(uniffi::Enum)] for enums
#[derive(Debug, uniffi::Record)]
pub struct MyNewType {
    pub field: String,
}
```

**3. Add the UniFFI entry point in `lib.rs`**
```rust
#[uniffi::export]
pub fn my_new_function(handle: Arc<TaskManagerHandle>, param: String) -> Result<TaskInfo, TaskError> {
    handle.manager.lock().unwrap().my_new_function(param)
}
```

**4. Add Rust tests**

**5. Add the Kotlin wrapper in `TaskRepository.kt`**
```kotlin
fun myNewFunction(param: String): TaskInfo {
    return taskManager.myNewFunction(param)
}
```

**6. Wire up in `TaskViewModel.kt` and the relevant composable screen**

**7. Regenerate bindings and verify**
```bash
./gradlew assembleDebug   # bindings regenerate here
./gradlew test
```

---

## Pull request process

1. **Open an issue first** for non-trivial changes — discuss the approach before writing code.
2. **Fork** the repository and create a branch: `git checkout -b feat/my-feature` or `fix/my-fix`.
3. **Write tests** for all new Rust functions. Android UI changes should at minimum compile cleanly.
4. **Run the full pipeline** locally before pushing:
   ```bash
   cd rust/taskgeneral-core && cargo fmt --check && cargo clippy -- -D warnings && cargo test
   ./gradlew assembleDebug && ./gradlew test
   ```
5. **Open a PR** against `main`. Fill in the PR template — describe what changed and why.
6. **CI must pass.** All three jobs (rust-tests, android-build, android-tests) are required.
7. A maintainer will review within a few days. Address feedback with new commits (don't force-push during review).

---

## Common build issues

**`cargo build` fails with `error: toolchain 'stable' is not installed`**
```bash
rustup toolchain install stable
```

**`./gradlew assembleDebug` fails with `NDK not found`**
Open Android Studio → SDK Manager → SDK Tools → check NDK (Side by side) → install r26d. Then ensure `ANDROID_NDK_HOME` or the local.properties `ndk.dir` points to it.

**`./gradlew assembleDebug` fails with `Could not find uniffi_taskgeneral_core.so`**
The Rust cross-compile didn't run. Check that your NDK is installed and the Rust Android targets are present:
```bash
rustup target list --installed | grep android
```

**UniFFI-generated Kotlin references an old function signature**
You probably edited `lib.rs` or `models.rs` without rebuilding. Run `./gradlew assembleDebug` to regenerate bindings.

**`cargo test` passes but `./gradlew test` fails with a class not found error**
The device/emulator tests require the Rust `.so` to be present. Run `./gradlew assembleDebug` first to ensure the native library is built.
