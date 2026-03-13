pub mod error;
pub mod models;
pub mod task_manager;

use error::Result;
use models::{SyncResult, TaskFilter, TaskInfo, TaskUpdate, WorkingSetItem};
use std::sync::{Arc, Mutex};
use task_manager::TaskManager;

uniffi::setup_scaffolding!();

#[uniffi::export]
pub fn version() -> String {
    env!("CARGO_PKG_VERSION").to_string()
}

#[uniffi::export]
pub fn create_task_manager(data_dir: String) -> Result<Arc<TaskManagerWrapper>> {
    let manager = TaskManager::new(&data_dir)?;
    Ok(Arc::new(TaskManagerWrapper {
        inner: Mutex::new(manager),
    }))
}

#[derive(uniffi::Object)]
pub struct TaskManagerWrapper {
    inner: Mutex<TaskManager>,
}

#[uniffi::export]
impl TaskManagerWrapper {
    pub fn create_task(&self, description: String) -> Result<TaskInfo> {
        self.inner.lock().unwrap().create_task(&description)
    }

    pub fn get_task(&self, uuid: String) -> Result<Option<TaskInfo>> {
        self.inner.lock().unwrap().get_task(&uuid)
    }

    pub fn list_tasks(&self) -> Result<Vec<TaskInfo>> {
        self.inner.lock().unwrap().list_tasks()
    }

    pub fn list_tasks_filtered(&self, filter: TaskFilter) -> Result<Vec<TaskInfo>> {
        self.inner.lock().unwrap().list_tasks_filtered(filter)
    }

    pub fn get_working_set(&self) -> Result<Vec<WorkingSetItem>> {
        self.inner.lock().unwrap().get_working_set()
    }

    pub fn update_task(&self, uuid: String, updates: TaskUpdate) -> Result<TaskInfo> {
        self.inner.lock().unwrap().update_task(&uuid, updates)
    }

    pub fn complete_task(&self, uuid: String) -> Result<TaskInfo> {
        self.inner.lock().unwrap().complete_task(&uuid)
    }

    pub fn uncomplete_task(&self, uuid: String) -> Result<TaskInfo> {
        self.inner.lock().unwrap().uncomplete_task(&uuid)
    }

    pub fn delete_task(&self, uuid: String) -> Result<()> {
        self.inner.lock().unwrap().delete_task(&uuid)
    }

    pub fn configure_sync(
        &self,
        server_url: String,
        encryption_secret: String,
        client_id: String,
    ) -> Result<()> {
        self.inner
            .lock()
            .unwrap()
            .configure_sync(&server_url, &encryption_secret, &client_id)
    }

    pub fn sync(&self) -> Result<SyncResult> {
        self.inner.lock().unwrap().sync()
    }

    pub fn clear_local_data(&self) -> Result<()> {
        self.inner.lock().unwrap().clear_local_data()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_version() {
        let v = version();
        assert!(!v.is_empty());
        assert_eq!(v, "0.1.0");
    }
}
