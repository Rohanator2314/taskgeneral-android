use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, uniffi::Record)]
pub struct TaskInfo {
    pub uuid: String,
    pub description: String,
    pub status: String,
    pub project: Option<String>,
    pub tags: Vec<String>,
    pub priority: Option<String>,
    pub entry: Option<String>,
    pub modified: Option<String>,
}

#[derive(Debug, Clone, Default, uniffi::Record)]
pub struct TaskUpdate {
    pub description: Option<String>,
    pub project: Option<String>,
    pub tags: Option<Vec<String>>,
    pub priority: Option<String>,
}

#[derive(Debug, Clone, Default, uniffi::Record)]
pub struct TaskFilter {
    pub status: Option<String>,
    pub project: Option<String>,
    pub tag: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct SyncResult {
    pub success: bool,
    pub message: String,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct WorkingSetItem {
    pub id: u64,
    pub task: TaskInfo,
}
