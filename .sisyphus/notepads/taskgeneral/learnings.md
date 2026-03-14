
## Task 14: Sync Error Handling
- **ViewModel Pattern**: 
  - Expose `syncStatus` as `StateFlow<String?>`.
  - On success: `_syncStatus.value = "Sync successful"`.
  - On failure: `_syncStatus.value = result.message` (where `result.message` contains "Sync failed: ...").
  - On exception: `_syncStatus.value = "Sync error: ${e.message}"`.
- **UI Feedback**:
  - `SyncSettingsScreen` observes `syncStatus`.
  - Color logic: Red if status contains "failed" or "error", Primary color otherwise.
  - This simple text-based feedback is sufficient for settings screens without complex dialogs.
- **Refinement**:
  - Rust `SyncResult` messages already include prefixes like "Sync failed: ". 
  - ViewModel should avoid adding double prefixes (e.g., "Sync failed: Sync failed: ...").
  - Direct assignment of the error message provides the cleanest UI string.

## Background Sync Implementation

### Implementation Approach
- Used Android WorkManager for reliable background task scheduling
- Created SyncWorker in `app/src/main/java/dev/rohans/taskwarrior/work/SyncWorker.kt`
- Configured periodic sync every 15 minutes (WorkManager minimum)
- Required network connectivity constraint for sync operations

### Key Components Added
1. **SyncWorker**: CoroutineWorker that performs background sync
   - Retrieves encrypted sync config from SharedPreferences
   - Initializes TaskRepository with stored credentials
   - Performs sync via repository.sync()
   - Returns Result.retry() on failures for automatic retry

2. **MainApplication**: Schedules background sync on app startup
   - Enqueues periodic work with KEEP policy (doesn't replace existing)
   - Sets network connectivity constraint
   - Uses unique work name to prevent duplicate scheduling

3. **TaskViewModel**: Triggers sync scheduling when config is saved
   - Added Context parameter to ViewModel and Factory
   - Calls scheduleSyncIfConfigured() after saving sync config
   - Uses REPLACE policy to update schedule with new config

### Configuration
- WorkManager dependency: `androidx.work:work-runtime-ktx:2.9.0`
- Minimum sync interval: 15 minutes (WorkManager limitation)
- Network constraint: CONNECTED (any network type)
- Retry policy: Automatic retry on failure (WorkManager default)

### Manual Sync Preservation
- Existing sync() method in TaskViewModel unchanged
- SyncSettingsScreen "Sync Now" button still functional
- Background sync and manual sync use same TaskRepository.sync() method

### Testing Results
- ./gradlew lintDebug: SUCCESSFUL
- ./gradlew test: SUCCESSFUL (all unit tests pass)
- No compilation errors or warnings introduced
