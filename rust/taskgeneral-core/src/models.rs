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
    pub due: Option<String>,
    pub wait: Option<String>,
    pub start: Option<String>,
    pub recur: Option<String>,
    pub urgency: f64,
    pub is_active: bool,
    pub is_waiting: bool,
}

#[derive(Debug, Clone, Default, uniffi::Record)]
pub struct TaskUpdate {
    pub description: Option<String>,
    pub project: Option<String>,
    pub tags: Option<Vec<String>>,
    pub priority: Option<String>,
    pub due: Option<String>,
    pub wait: Option<String>,
    pub recur: Option<String>,
}

#[derive(Debug, Clone, Default, uniffi::Record)]
pub struct TaskFilter {
    pub status: Option<String>,
    pub project: Option<String>,
    pub tag: Option<String>,
    pub sort_by: Option<String>,
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

#[derive(Debug, Clone, PartialEq, uniffi::Enum)]
pub enum SortField {
    Urgency,
    DueDate,
    Priority,
    EntryDate,
    Modified,
    Description,
}
