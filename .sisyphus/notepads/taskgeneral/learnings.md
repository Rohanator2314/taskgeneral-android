
## Task 10: Task Edit Screen Refinement
- **UI Components**:
  - Used `FlowRow` for tags display, which wraps nicely.
  - Used `ExposedDropdownMenuBox` for Priority selection, providing a standard M3 dropdown experience.
  - Implemented `InputChip` for tags, allowing easy removal.
- **Input Handling**:
  - Tags are added via `OutlinedTextField` by detecting comma or newline.
  - Form validation for description ensures no empty tasks are created.
- **Navigation**:
  - Cancel button simply pops back stack.
  - Save button performs `createTask` -> `updateTask` sequence for new tasks to handle all fields.
- **TaskEditScreen**: Enabled tag removal by adding `Modifier.clickable` to the `InputChip` trailing icon, as the chip's `onClick` was reserved for potential future use or non-removal actions.

## Task 11: Sync Settings Screen Implementation
- **UI Components**:
  - Implemented `SyncSettingsScreen` using Material 3 components.
  - Replaced `IconButton` with `TextButton` for password visibility toggle to avoid dependency issues with missing icons.
- **Data Persistence**:
  - Used `SharedPreferences` in `TaskViewModel` to persist sync configuration (Server URL, Encryption Secret, Client ID) because `TaskRepository` (Rust core) does not expose a way to read back the configuration once set.
  - Auto-generated Client ID (UUID) on first load if missing.
- **ViewModel Logic**:
  - Implemented `saveSyncConfig` in ViewModel which updates local state, saves to Prefs, and calls `repository.configureSync`.
  - Implemented `sync` in ViewModel which calls `repository.sync` and updates status.
- **Refactoring**:
  - Updated `TaskViewModelFactory` to inject `SharedPreferences`.
  - Updated `TaskListScreen` and `TaskEditScreen` to pass the required dependencies to the ViewModel factory.
