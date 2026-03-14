use thiserror::Error;

#[derive(Error, Debug, uniffi::Error)]
#[uniffi(flat_error)]
pub enum TaskError {
    #[error("Task not found: {0}")]
    TaskNotFound(String),

    #[error("Invalid UUID: {0}")]
    InvalidUuid(String),

    #[error("Invalid description: {0}")]
    InvalidDescription(String),

    #[error("Storage error: {0}")]
    StorageError(String),

    #[error("Invalid status: {0}")]
    InvalidStatus(String),

    #[error("Invalid priority: {0}")]
    InvalidPriority(String),

    #[error("TaskChampion error: {0}")]
    TaskChampionError(String),

    #[error("IO error: {0}")]
    IoError(String),

    #[error("Sync not configured")]
    SyncNotConfigured,

    #[error("Invalid date: {0}")]
    InvalidDate(String),

    #[error("Invalid recurrence: {0}")]
    InvalidRecurrence(String),

    #[error("Invalid sync server URL: {0}")]
    InvalidSyncUrl(String),

    #[error("Sync error: {0}")]
    SyncError(String),
}

impl From<std::io::Error> for TaskError {
    fn from(err: std::io::Error) -> Self {
        TaskError::IoError(err.to_string())
    }
}

impl From<taskchampion::Error> for TaskError {
    fn from(err: taskchampion::Error) -> Self {
        TaskError::TaskChampionError(err.to_string())
    }
}

impl From<uuid::Error> for TaskError {
    fn from(err: uuid::Error) -> Self {
        TaskError::InvalidUuid(err.to_string())
    }
}

impl From<chrono::ParseError> for TaskError {
    fn from(err: chrono::ParseError) -> Self {
        TaskError::InvalidDate(err.to_string())
    }
}

pub type Result<T> = std::result::Result<T, TaskError>;
