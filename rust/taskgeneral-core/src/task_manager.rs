use crate::error::{Result, TaskError};
use crate::models::{SortField, SyncResult, TaskFilter, TaskInfo, TaskUpdate};
use chrono::{DateTime, Utc};
use std::path::PathBuf;
use taskchampion::{
    server::ServerConfig,
    storage::AccessMode,
    Operations, Replica, SqliteStorage, Status, Tag, Task, Uuid as TcUuid,
};
use tokio::runtime::Runtime;

pub struct TaskManager {
    replica: Replica<SqliteStorage>,
    runtime: Runtime,
    data_dir: PathBuf,
    sync_url: Option<String>,
    sync_client_id: Option<TcUuid>,
    sync_secret: Option<Vec<u8>>,
}

impl TaskManager {
    pub fn new(data_dir: &str) -> Result<Self> {
        let runtime = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()?;
        let path = PathBuf::from(data_dir);
        
        let replica = runtime.block_on(async {
            let storage = SqliteStorage::new(path.clone(), AccessMode::ReadWrite, true).await?;
            Ok::<Replica<SqliteStorage>, taskchampion::Error>(Replica::new(storage))
        })?;

        Ok(TaskManager { 
            replica, 
            runtime,
            data_dir: path,
            sync_url: None,
            sync_client_id: None,
            sync_secret: None,
        })
    }

    pub fn create_task(&mut self, description: &str) -> Result<TaskInfo> {
        if description.is_empty() {
            return Err(TaskError::InvalidDescription(
                "Description cannot be empty".to_string(),
            ));
        }

        let uuid = TcUuid::new_v4();

        self.runtime.block_on(async {
            let mut ops = Operations::new();
            let mut task: Task = self.replica.create_task(uuid, &mut ops).await?;
            
            task.set_description(description.to_string(), &mut ops)?;
            task.set_status(Status::Pending, &mut ops)?;
            task.set_entry(Some(chrono::Utc::now()), &mut ops)?;

            self.replica.commit_operations(ops).await?;
            
            Ok::<(), taskchampion::Error>(())
        })?;
        
        self.get_task(&uuid.to_string())?
            .ok_or_else(|| TaskError::TaskNotFound(uuid.to_string()))
    }

    pub fn get_task(&mut self, uuid_str: &str) -> Result<Option<TaskInfo>> {
        let uuid = TcUuid::parse_str(uuid_str)?;

        self.runtime.block_on(async {
            let task = self.replica.get_task(uuid).await?;
            
            match task {
                Some(task) => Ok(Some(task_to_info(&task))),
                None => Ok(None),
            }
        })
    }

    pub fn list_tasks(&mut self) -> Result<Vec<TaskInfo>> {
        self.runtime.block_on(async {
            let uuids = self.replica.all_task_uuids().await?;
            let mut tasks = Vec::new();

            for uuid in uuids {
                if let Some(task) = self.replica.get_task(uuid).await? {
                    tasks.push(task_to_info(&task));
                }
            }

            Ok(tasks)
        })
    }

    pub fn list_tasks_filtered(&mut self, filter: TaskFilter) -> Result<Vec<TaskInfo>> {
        self.runtime.block_on(async {
            let uuids = self.replica.all_task_uuids().await?;
            let mut tasks = Vec::new();

            for uuid in uuids {
                if let Some(task) = self.replica.get_task(uuid).await? {
                    let task_info = task_to_info(&task);
                    
                    // Apply filters
                    if let Some(ref status_filter) = filter.status {
                        if task_info.status != *status_filter {
                            continue;
                        }
                    }
                    
                    if let Some(ref project_filter) = filter.project {
                        match &task_info.project {
                            Some(project) if project == project_filter => {},
                            _ => continue,
                        }
                    }
                    
                    if let Some(ref tag_filter) = filter.tag {
                        if !task_info.tags.contains(tag_filter) {
                            continue;
                        }
                    }
                    
                    tasks.push(task_info);
                }
            }

            Ok(tasks)
        })
    }

    pub fn list_tasks_sorted(&mut self, filter: TaskFilter, sort_by: SortField) -> Result<Vec<TaskInfo>> {
        self.runtime.block_on(async {
            let uuids = self.replica.all_task_uuids().await?;
            let mut tasks = Vec::new();

            for uuid in uuids {
                if let Some(task) = self.replica.get_task(uuid).await? {
                    let task_info = task_to_info(&task);

                    // Apply waiting task auto-hide: if no status filter, exclude waiting tasks
                    let status_filter_set = filter.status.is_some();
                    if !status_filter_set && task_info.is_waiting {
                        continue;
                    }

                    // Apply filters (same logic as list_tasks_filtered)
                    if let Some(ref status_filter) = filter.status {
                        if task_info.status != *status_filter {
                            continue;
                        }
                    }

                    if let Some(ref project_filter) = filter.project {
                        match &task_info.project {
                            Some(project) if project == project_filter => {},
                            _ => continue,
                        }
                    }

                    if let Some(ref tag_filter) = filter.tag {
                        if !task_info.tags.contains(tag_filter) {
                            continue;
                        }
                    }

                    tasks.push(task_info);
                }
            }

            // Sort based on sort_by field
            match sort_by {
                SortField::Urgency => {
                    tasks.sort_by(|a, b| {
                        b.urgency.partial_cmp(&a.urgency).unwrap_or(std::cmp::Ordering::Equal)
                    });
                }
                SortField::DueDate => {
                    tasks.sort_by(|a, b| {
                        match (&a.due, &b.due) {
                            (Some(da), Some(db)) => da.cmp(db),
                            (Some(_), None) => std::cmp::Ordering::Less,    // tasks with due date first
                            (None, Some(_)) => std::cmp::Ordering::Greater,  // no-due-date last
                            (None, None) => std::cmp::Ordering::Equal,
                        }
                    });
                }
                SortField::Priority => {
                    let priority_rank = |p: &Option<String>| match p.as_deref() {
                        Some("H") => 3,
                        Some("M") => 2,
                        Some("L") => 1,
                        _ => 0,
                    };
                    tasks.sort_by(|a, b| {
                        priority_rank(&b.priority).cmp(&priority_rank(&a.priority))
                    });
                }
                SortField::EntryDate => {
                    tasks.sort_by(|a, b| {
                        b.entry.cmp(&a.entry)  // RFC3339 strings sort lexicographically = chronologically
                    });
                }
                SortField::Modified => {
                    tasks.sort_by(|a, b| {
                        b.modified.cmp(&a.modified)
                    });
                }
                SortField::Description => {
                    tasks.sort_by(|a, b| {
                        a.description.cmp(&b.description)
                    });
                }
            }

            Ok(tasks)
        })
    }

    pub fn get_working_set(&mut self) -> Result<Vec<crate::models::WorkingSetItem>> {
        self.runtime.block_on(async {
            let working_set = self.replica.working_set().await?;
            let mut result = Vec::new();

            for (id, uuid) in working_set.iter() {
                if let Some(task) = self.replica.get_task(uuid).await? {
                    result.push(crate::models::WorkingSetItem {
                        id: id as u64,
                        task: task_to_info(&task),
                    });
                }
            }

            Ok(result)
        })
    }

    pub fn update_task(&mut self, uuid_str: &str, updates: TaskUpdate) -> Result<TaskInfo> {
        let uuid = TcUuid::parse_str(uuid_str)?;

        let result = self.runtime.block_on(async {
            let task_opt = self.replica.get_task(uuid).await?;
            Ok::<Option<Task>, taskchampion::Error>(task_opt)
        })?;
        
        let mut task: Task = result.ok_or_else(|| TaskError::TaskNotFound(uuid_str.to_string()))?;

        if let Some(description) = &updates.description {
            if description.is_empty() {
                return Err(TaskError::InvalidDescription(
                    "Description cannot be empty".to_string(),
                ));
            }
        }

        if let Some(priority) = &updates.priority {
            if !["H", "M", "L", ""].contains(&priority.as_str()) {
                return Err(TaskError::InvalidPriority(format!(
                    "Priority must be H, M, L, or empty. Got: {}",
                    priority
                )));
            }
        }

        // Validate due/wait dates BEFORE block_on
        let due_to_set: Option<Option<DateTime<Utc>>> = if let Some(due_str) = &updates.due {
            if due_str.is_empty() {
                Some(None)
            } else {
                Some(Some(parse_rfc3339(due_str)?))
            }
        } else {
            None
        };

        let wait_to_set: Option<Option<DateTime<Utc>>> = if let Some(wait_str) = &updates.wait {
            if wait_str.is_empty() {
                Some(None)
            } else {
                Some(Some(parse_rfc3339(wait_str)?))
            }
        } else {
            None
        };

        // Validate recurrence
        if let Some(recur_str) = &updates.recur {
            let allowed = ["", "daily", "weekly", "biweekly", "monthly", "quarterly", "yearly"];
            if !allowed.contains(&recur_str.as_str()) {
                return Err(TaskError::InvalidRecurrence(format!(
                    "Recurrence must be one of: daily, weekly, biweekly, monthly, quarterly, yearly. Got: {}",
                    recur_str
                )));
            }
        }

        self.runtime.block_on(async {
            let mut ops = Operations::new();

            if let Some(description) = updates.description {
                task.set_description(description, &mut ops)?;
            }

            if let Some(project) = updates.project {
                if project.is_empty() {
                    task.set_value("project", None, &mut ops)?;
                } else {
                    task.set_value("project", Some(project), &mut ops)?;
                }
            }

            if let Some(tags) = updates.tags {
                let current_tags: Vec<_> = task.get_tags()
                    .filter(|t| !t.is_synthetic())
                    .collect();
                for tag in current_tags {
                    task.remove_tag(&tag, &mut ops)?;
                }
                
                for tag_str in tags {
                    let tag: Tag = tag_str.parse().map_err(|_| {
                        taskchampion::Error::from(std::io::Error::new(
                            std::io::ErrorKind::InvalidInput,
                            format!("Invalid tag: {}", tag_str)
                        ))
                    })?;
                    task.add_tag(&tag, &mut ops)?;
                }
            }

            if let Some(priority) = updates.priority {
                if priority.is_empty() {
                    task.set_priority(String::new(), &mut ops)?;
                } else {
                    task.set_priority(priority, &mut ops)?;
                }
            }

            if let Some(due_opt) = due_to_set {
                task.set_due(due_opt, &mut ops)?;
            }

            if let Some(wait_opt) = wait_to_set {
                task.set_wait(wait_opt, &mut ops)?;
            }

            if let Some(recur_str) = updates.recur {
                if recur_str.is_empty() {
                    task.set_value("recur", None, &mut ops)?;
                } else {
                    task.set_value("recur", Some(recur_str), &mut ops)?;
                }
            }

            self.replica.commit_operations(ops).await?;

            Ok::<(), taskchampion::Error>(())
        })?;
        
        self.get_task(uuid_str)?
            .ok_or_else(|| TaskError::TaskNotFound(uuid_str.to_string()))
    }

    pub fn complete_task(&mut self, uuid_str: &str) -> Result<TaskInfo> {
        let uuid = TcUuid::parse_str(uuid_str)?;

        let result = self.runtime.block_on(async {
            let task_opt = self.replica.get_task(uuid).await?;
            Ok::<Option<Task>, taskchampion::Error>(task_opt)
        })?;
        
        let mut task = result.ok_or_else(|| TaskError::TaskNotFound(uuid_str.to_string()))?;

        self.runtime.block_on(async {
            let mut ops = Operations::new();
            task.done(&mut ops)?;

            self.replica.commit_operations(ops).await?;

            Ok::<(), taskchampion::Error>(())
        })?;
        
        self.get_task(uuid_str)?
            .ok_or_else(|| TaskError::TaskNotFound(uuid_str.to_string()))
    }

    pub fn start_task(&mut self, uuid_str: &str) -> Result<TaskInfo> {
        let uuid = TcUuid::parse_str(uuid_str)?;

        let result = self.runtime.block_on(async {
            let task_opt = self.replica.get_task(uuid).await?;
            Ok::<Option<Task>, taskchampion::Error>(task_opt)
        })?;
        
        let mut task = result.ok_or_else(|| TaskError::TaskNotFound(uuid_str.to_string()))?;

        if task.is_active() {
            return Err(TaskError::InvalidStatus(
                "Task is already active (already started)".to_string()
            ));
        }

        self.runtime.block_on(async {
            let mut ops = Operations::new();
            task.start(&mut ops)?;
            self.replica.commit_operations(ops).await?;
            Ok::<(), taskchampion::Error>(())
        })?;
        
        self.get_task(uuid_str)?
            .ok_or_else(|| TaskError::TaskNotFound(uuid_str.to_string()))
    }

    pub fn stop_task(&mut self, uuid_str: &str) -> Result<TaskInfo> {
        let uuid = TcUuid::parse_str(uuid_str)?;

        let result = self.runtime.block_on(async {
            let task_opt = self.replica.get_task(uuid).await?;
            Ok::<Option<Task>, taskchampion::Error>(task_opt)
        })?;
        
        let mut task = result.ok_or_else(|| TaskError::TaskNotFound(uuid_str.to_string()))?;

        if !task.is_active() {
            return Err(TaskError::InvalidStatus(
                "Task is not active (not started)".to_string()
            ));
        }

        self.runtime.block_on(async {
            let mut ops = Operations::new();
            task.stop(&mut ops)?;
            self.replica.commit_operations(ops).await?;
            Ok::<(), taskchampion::Error>(())
        })?;
        
        self.get_task(uuid_str)?
            .ok_or_else(|| TaskError::TaskNotFound(uuid_str.to_string()))
    }

    pub fn uncomplete_task(&mut self, uuid_str: &str) -> Result<TaskInfo> {
        let uuid = TcUuid::parse_str(uuid_str)?;

        let result = self.runtime.block_on(async {
            let task_opt = self.replica.get_task(uuid).await?;
            Ok::<Option<Task>, taskchampion::Error>(task_opt)
        })?;
        
        let mut task = result.ok_or_else(|| TaskError::TaskNotFound(uuid_str.to_string()))?;

        self.runtime.block_on(async {
            let mut ops = Operations::new();
            task.set_status(Status::Pending, &mut ops)?;

            self.replica.commit_operations(ops).await?;

            Ok::<(), taskchampion::Error>(())
        })?;
        
        self.get_task(uuid_str)?
            .ok_or_else(|| TaskError::TaskNotFound(uuid_str.to_string()))
    }

    pub fn delete_task(&mut self, uuid_str: &str) -> Result<()> {
        let uuid = TcUuid::parse_str(uuid_str)?;

        let result = self.runtime.block_on(async {
            let task_opt = self.replica.get_task(uuid).await?;
            Ok::<Option<Task>, taskchampion::Error>(task_opt)
        })?;
        
        let mut task = result.ok_or_else(|| TaskError::TaskNotFound(uuid_str.to_string()))?;

        self.runtime.block_on(async {
            let mut ops = Operations::new();
            task.set_status(Status::Deleted, &mut ops)?;

            self.replica.commit_operations(ops).await?;

            Ok::<(), taskchampion::Error>(())
        })?;
        
        Ok(())
    }

    pub fn configure_sync(
        &mut self,
        server_url: &str,
        encryption_secret: &str,
        client_id: &str,
    ) -> Result<()> {
        if server_url.is_empty() {
            return Err(TaskError::InvalidSyncUrl(
                "Server URL cannot be empty".to_string(),
            ));
        }

        if !server_url.starts_with("http://") && !server_url.starts_with("https://") {
            return Err(TaskError::InvalidSyncUrl(format!(
                "Server URL must start with http:// or https://, got: {}",
                server_url
            )));
        }

        let client_uuid = client_id.parse().map_err(|e| {
            TaskError::InvalidSyncUrl(format!("Invalid client_id UUID: {}", e))
        })?;

        self.sync_url = Some(server_url.to_string());
        self.sync_client_id = Some(client_uuid);
        self.sync_secret = Some(encryption_secret.as_bytes().to_vec());
        
        Ok(())
    }

    pub fn sync(&mut self) -> Result<SyncResult> {
        let url = self.sync_url.clone().ok_or(TaskError::SyncNotConfigured)?;
        let client_id = self.sync_client_id.ok_or(TaskError::SyncNotConfigured)?;
        let secret = self.sync_secret.clone().ok_or(TaskError::SyncNotConfigured)?;

        let result = self.runtime.block_on(async {
            let config = ServerConfig::Remote {
                url,
                client_id,
                encryption_secret: secret,
            };
            let mut server = config.into_server().await?;
            self.replica.sync(&mut server, false).await
        });

        match result {
            Ok(_) => Ok(SyncResult {
                success: true,
                message: "Sync completed successfully".to_string(),
            }),
            Err(e) => Ok(SyncResult {
                success: false,
                message: format!("Sync failed: {}", e),
            }),
        }
    }

    pub fn clear_local_data(&mut self) -> Result<()> {
        let data_dir = self.data_dir.clone();
        let db_file = data_dir.join("taskchampion.sqlite3");
        let wal_file = data_dir.join("taskchampion.sqlite3-wal");
        let shm_file = data_dir.join("taskchampion.sqlite3-shm");

        let placeholder = self.runtime.block_on(async {
            let storage =
                SqliteStorage::new(data_dir.clone(), AccessMode::ReadWrite, true).await?;
            Ok::<Replica<SqliteStorage>, taskchampion::Error>(Replica::new(storage))
        })?;

        let _old_replica = std::mem::replace(&mut self.replica, placeholder);
        drop(_old_replica);

        for f in [&db_file, &wal_file, &shm_file] {
            let _ = std::fs::remove_file(f);
        }

        let fresh_replica = self.runtime.block_on(async {
            let storage =
                SqliteStorage::new(data_dir, AccessMode::ReadWrite, true).await?;
            Ok::<Replica<SqliteStorage>, taskchampion::Error>(Replica::new(storage))
        })?;

        self.replica = fresh_replica;
        self.sync_url = None;
        self.sync_client_id = None;
        self.sync_secret = None;
        Ok(())
    }
}

fn due_urgency(task: &Task) -> f64 {
    match task.get_due() {
        None => 0.0,
        Some(due) => {
            let now = chrono::Utc::now();
            let days_overdue = (now - due).num_seconds() as f64 / 86400.0;
            if days_overdue >= 7.0 {
                1.0
            } else if days_overdue >= -14.0 {
                ((days_overdue + 14.0) * 0.8 / 21.0) + 0.2
            } else {
                0.2
            }
        }
    }
}

fn age_urgency(task: &Task) -> f64 {
    match task.get_entry() {
        None => 0.0,
        Some(entry) => {
            let now = chrono::Utc::now();
            let age_days = (now - entry).num_seconds() as f64 / 86400.0;
            (age_days / 365.0).clamp(0.0, 1.0)
        }
    }
}

fn tag_urgency(task: &Task) -> f64 {
    let count = task.get_tags().filter(|t| !t.is_synthetic()).count();
    match count {
        0 => 0.0,
        1 => 0.8,
        2 => 0.9,
        _ => 1.0,
    }
}

fn parse_rfc3339(s: &str) -> Result<DateTime<Utc>> {
    chrono::DateTime::parse_from_rfc3339(s)
        .map(|dt| dt.with_timezone(&Utc))
        .map_err(|e| crate::error::TaskError::InvalidDate(e.to_string()))
}

fn calculate_urgency(task: &Task) -> f64 {
    let priority = task.get_priority();
    let has_next = task.get_tags()
        .filter(|t| !t.is_synthetic())
        .any(|t| t.to_string() == "next");
    let has_project = task.get_value("project").is_some();

    15.0 * if has_next { 1.0 } else { 0.0 }
        + 12.0 * due_urgency(task)
        + 6.0 * if priority == "H" { 1.0 } else { 0.0 }
        + 4.0 * if task.is_active() { 1.0 } else { 0.0 }
        + 3.9 * if priority == "M" { 1.0 } else { 0.0 }
        + 2.0 * age_urgency(task)
        + 1.8 * if priority == "L" { 1.0 } else { 0.0 }
        + 1.0 * tag_urgency(task)
        + 1.0 * if has_project { 1.0 } else { 0.0 }
        + (-3.0) * if task.is_waiting() { 1.0 } else { 0.0 }
}

fn task_to_info(task: &Task) -> TaskInfo {
    let tags: Vec<String> = task.get_tags()
        .filter(|t| !t.is_synthetic())
        .map(|t| t.to_string())
        .collect();

    TaskInfo {
        uuid: task.get_uuid().to_string(),
        description: task.get_description().to_string(),
        status: format!("{:?}", task.get_status()).to_lowercase(),
        project: task.get_value("project").map(|s| s.to_string()),
        tags,
        priority: {
            let p = task.get_priority();
            if p.is_empty() { None } else { Some(p.to_string()) }
        },
        entry: task.get_entry().map(|dt| dt.to_rfc3339()),
        modified: task.get_modified().map(|dt| dt.to_rfc3339()),
        due: task.get_due().map(|dt| dt.to_rfc3339()),
        wait: task.get_wait().map(|dt| dt.to_rfc3339()),
        start: task.get_value("start").map(|s| s.to_string()),
        recur: task.get_value("recur").map(|s| s.to_string()),
        urgency: calculate_urgency(task),
        is_active: task.is_active(),
        is_waiting: task.is_waiting(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;
    use std::path::PathBuf;

    struct TestTempDir {
        path: PathBuf,
    }

    impl TestTempDir {
        fn new() -> Self {
            let mut path = std::env::temp_dir();
            path.push(format!("taskgeneral-test-{}", TcUuid::new_v4()));
            fs::create_dir_all(&path).unwrap();
            TestTempDir { path }
        }

        fn path(&self) -> &std::path::Path {
            &self.path
        }
    }

    impl Drop for TestTempDir {
        fn drop(&mut self) {
            let _ = fs::remove_dir_all(&self.path);
        }
    }

    fn create_test_manager() -> (TaskManager, TestTempDir) {
        let temp_dir = TestTempDir::new();
        let manager = TaskManager::new(temp_dir.path().to_str().unwrap()).unwrap();
        (manager, temp_dir)
    }

    #[test]
    fn test_create_task() {
        let (mut manager, _temp) = create_test_manager();
        
        let task = manager.create_task("Test task").unwrap();
        
        assert_eq!(task.description, "Test task");
        assert_eq!(task.status, "pending");
        assert!(!task.uuid.is_empty());
        assert!(task.entry.is_some());
        assert!(task.modified.is_some());
    }

    #[test]
    fn test_create_task_empty_description() {
        let (mut manager, _temp) = create_test_manager();
        
        let result = manager.create_task("");
        
        assert!(result.is_err());
        match result {
            Err(TaskError::InvalidDescription(_)) => {},
            _ => panic!("Expected InvalidDescription error"),
        }
    }

    #[test]
    fn test_get_task() {
        let (mut manager, _temp) = create_test_manager();
        
        let created = manager.create_task("Test task").unwrap();
        let fetched = manager.get_task(&created.uuid).unwrap();
        
        assert!(fetched.is_some());
        let fetched = fetched.unwrap();
        assert_eq!(fetched.uuid, created.uuid);
        assert_eq!(fetched.description, "Test task");
    }

    #[test]
    fn test_get_task_not_found() {
        let (mut manager, _temp) = create_test_manager();
        
        let uuid = TcUuid::new_v4();
        let result = manager.get_task(&uuid.to_string()).unwrap();
        
        assert!(result.is_none());
    }

    #[test]
    fn test_list_tasks() {
        let (mut manager, _temp) = create_test_manager();
        
        manager.create_task("Task 1").unwrap();
        manager.create_task("Task 2").unwrap();
        manager.create_task("Task 3").unwrap();
        
        let tasks = manager.list_tasks().unwrap();
        
        assert_eq!(tasks.len(), 3);
        let descriptions: Vec<_> = tasks.iter().map(|t| t.description.as_str()).collect();
        assert!(descriptions.contains(&"Task 1"));
        assert!(descriptions.contains(&"Task 2"));
        assert!(descriptions.contains(&"Task 3"));
    }

    #[test]
    fn test_update_task_description() {
        let (mut manager, _temp) = create_test_manager();
        
        let task = manager.create_task("Original").unwrap();
        
        let updates = TaskUpdate {
            description: Some("Updated".to_string()),
            ..Default::default()
        };
        let updated = manager.update_task(&task.uuid, updates).unwrap();
        
        assert_eq!(updated.description, "Updated");
        assert_eq!(updated.uuid, task.uuid);
    }

    #[test]
    fn test_complete_task() {
        let (mut manager, _temp) = create_test_manager();
        
        let task = manager.create_task("Task to complete").unwrap();
        assert_eq!(task.status, "pending");
        
        let completed = manager.complete_task(&task.uuid).unwrap();
        
        assert_eq!(completed.status, "completed");
        assert_eq!(completed.uuid, task.uuid);
    }

    #[test]
    fn test_delete_task() {
        let (mut manager, _temp) = create_test_manager();
        
        let task = manager.create_task("Task to delete").unwrap();
        
        manager.delete_task(&task.uuid).unwrap();
        
        let fetched = manager.get_task(&task.uuid).unwrap();
        assert!(fetched.is_some());
        assert_eq!(fetched.unwrap().status, "deleted");
    }

    #[test]
    fn test_set_task_project() {
        let (mut manager, _temp) = create_test_manager();
        
        let task = manager.create_task("Task with project").unwrap();
        
        let updates = TaskUpdate {
            project: Some("my-project".to_string()),
            ..Default::default()
        };
        let updated = manager.update_task(&task.uuid, updates).unwrap();
        
        assert_eq!(updated.project, Some("my-project".to_string()));
    }

    #[test]
    fn test_add_task_tag() {
        let (mut manager, _temp) = create_test_manager();
        
        let task = manager.create_task("Task with tags").unwrap();
        
        let updates = TaskUpdate {
            tags: Some(vec!["work".to_string(), "urgent".to_string()]),
            ..Default::default()
        };
        let updated = manager.update_task(&task.uuid, updates).unwrap();
        
        assert_eq!(updated.tags.len(), 2);
        assert!(updated.tags.contains(&"work".to_string()));
        assert!(updated.tags.contains(&"urgent".to_string()));
    }

    #[test]
    fn test_remove_task_tag() {
        let (mut manager, _temp) = create_test_manager();
        
        let task = manager.create_task("Task with tags").unwrap();
        
        let updates = TaskUpdate {
            tags: Some(vec!["work".to_string()]),
            ..Default::default()
        };
        manager.update_task(&task.uuid, updates).unwrap();
        
        let updates = TaskUpdate {
            tags: Some(vec![]),
            ..Default::default()
        };
        let updated = manager.update_task(&task.uuid, updates).unwrap();
        
        assert_eq!(updated.tags.len(), 0);
    }

    #[test]
    fn test_set_task_priority() {
        let (mut manager, _temp) = create_test_manager();
        
        let task = manager.create_task("Task with priority").unwrap();
        
        let updates = TaskUpdate {
            priority: Some("H".to_string()),
            ..Default::default()
        };
        let updated = manager.update_task(&task.uuid, updates).unwrap();
        
        assert_eq!(updated.priority, Some("H".to_string()));
    }

    #[test]
    fn test_set_invalid_priority() {
        let (mut manager, _temp) = create_test_manager();
        
        let task = manager.create_task("Task").unwrap();
        
        let updates = TaskUpdate {
            priority: Some("X".to_string()),
            ..Default::default()
        };
        let result = manager.update_task(&task.uuid, updates);
        
        assert!(result.is_err());
        match result {
            Err(TaskError::InvalidPriority(_)) => {},
            _ => panic!("Expected InvalidPriority error"),
        }
    }

    #[test]
    fn test_filter_by_status_pending() {
        let (mut manager, _temp) = create_test_manager();
        
        let task1 = manager.create_task("Pending task 1").unwrap();
        let task2 = manager.create_task("Pending task 2").unwrap();
        let task3 = manager.create_task("Task to complete").unwrap();
        manager.complete_task(&task3.uuid).unwrap();
        
        let filter = TaskFilter {
            status: Some("pending".to_string()),
            ..Default::default()
        };
        let filtered = manager.list_tasks_filtered(filter).unwrap();
        
        assert_eq!(filtered.len(), 2);
        let uuids: Vec<_> = filtered.iter().map(|t| &t.uuid).collect();
        assert!(uuids.contains(&&task1.uuid));
        assert!(uuids.contains(&&task2.uuid));
        assert!(!uuids.contains(&&task3.uuid));
    }

    #[test]
    fn test_filter_by_status_completed() {
        let (mut manager, _temp) = create_test_manager();
        
        manager.create_task("Pending task").unwrap();
        let task2 = manager.create_task("Task to complete").unwrap();
        manager.complete_task(&task2.uuid).unwrap();
        
        let filter = TaskFilter {
            status: Some("completed".to_string()),
            ..Default::default()
        };
        let filtered = manager.list_tasks_filtered(filter).unwrap();
        
        assert_eq!(filtered.len(), 1);
        assert_eq!(filtered[0].uuid, task2.uuid);
        assert_eq!(filtered[0].status, "completed");
    }

    #[test]
    fn test_filter_by_project() {
        let (mut manager, _temp) = create_test_manager();
        
        let task1 = manager.create_task("Task in project A").unwrap();
        let updates = TaskUpdate {
            project: Some("projectA".to_string()),
            ..Default::default()
        };
        manager.update_task(&task1.uuid, updates).unwrap();
        
        let task2 = manager.create_task("Task in project B").unwrap();
        let updates = TaskUpdate {
            project: Some("projectB".to_string()),
            ..Default::default()
        };
        manager.update_task(&task2.uuid, updates).unwrap();
        
        manager.create_task("Task with no project").unwrap();
        
        let filter = TaskFilter {
            project: Some("projectA".to_string()),
            ..Default::default()
        };
        let filtered = manager.list_tasks_filtered(filter).unwrap();
        
        assert_eq!(filtered.len(), 1);
        assert_eq!(filtered[0].uuid, task1.uuid);
        assert_eq!(filtered[0].project, Some("projectA".to_string()));
    }

    #[test]
    fn test_filter_by_tag() {
        let (mut manager, _temp) = create_test_manager();
        
        let task1 = manager.create_task("Task with tag").unwrap();
        let updates = TaskUpdate {
            tags: Some(vec!["work".to_string()]),
            ..Default::default()
        };
        manager.update_task(&task1.uuid, updates).unwrap();
        
        let task2 = manager.create_task("Task with other tag").unwrap();
        let updates = TaskUpdate {
            tags: Some(vec!["personal".to_string()]),
            ..Default::default()
        };
        manager.update_task(&task2.uuid, updates).unwrap();
        
        manager.create_task("Task with no tags").unwrap();
        
        let filter = TaskFilter {
            tag: Some("work".to_string()),
            ..Default::default()
        };
        let filtered = manager.list_tasks_filtered(filter).unwrap();
        
        assert_eq!(filtered.len(), 1);
        assert_eq!(filtered[0].uuid, task1.uuid);
        assert!(filtered[0].tags.contains(&"work".to_string()));
    }

    #[test]
    fn test_filter_combined() {
        let (mut manager, _temp) = create_test_manager();
        
        let task1 = manager.create_task("Pending task in projectA with work tag").unwrap();
        let updates = TaskUpdate {
            project: Some("projectA".to_string()),
            tags: Some(vec!["work".to_string()]),
            ..Default::default()
        };
        manager.update_task(&task1.uuid, updates).unwrap();
        
        let task2 = manager.create_task("Pending task in projectA with personal tag").unwrap();
        let updates = TaskUpdate {
            project: Some("projectA".to_string()),
            tags: Some(vec!["personal".to_string()]),
            ..Default::default()
        };
        manager.update_task(&task2.uuid, updates).unwrap();
        
        let task3 = manager.create_task("Completed task in projectA with work tag").unwrap();
        let updates = TaskUpdate {
            project: Some("projectA".to_string()),
            tags: Some(vec!["work".to_string()]),
            ..Default::default()
        };
        manager.update_task(&task3.uuid, updates).unwrap();
        manager.complete_task(&task3.uuid).unwrap();
        
        let filter = TaskFilter {
            status: Some("pending".to_string()),
            project: Some("projectA".to_string()),
            tag: Some("work".to_string()),
            sort_by: None,
        };
        let filtered = manager.list_tasks_filtered(filter).unwrap();
        
        assert_eq!(filtered.len(), 1);
        assert_eq!(filtered[0].uuid, task1.uuid);
    }

    #[test]
    fn test_empty_filter() {
        let (mut manager, _temp) = create_test_manager();
        
        manager.create_task("Task 1").unwrap();
        manager.create_task("Task 2").unwrap();
        let task3 = manager.create_task("Task 3").unwrap();
        manager.complete_task(&task3.uuid).unwrap();
        
        let filter = TaskFilter::default();
        let filtered = manager.list_tasks_filtered(filter).unwrap();
        
        assert_eq!(filtered.len(), 3);
    }

     #[test]
     fn test_working_set() {
         let (mut manager, _temp) = create_test_manager();
         
         manager.create_task("Pending task 1").unwrap();
         manager.create_task("Pending task 2").unwrap();
         let task3 = manager.create_task("Task to complete").unwrap();
         manager.complete_task(&task3.uuid).unwrap();
         
         let working_set = manager.get_working_set().unwrap();
         
         let pending_tasks: Vec<_> = working_set.iter().filter(|item| item.task.status == "pending").collect();
         assert_eq!(pending_tasks.len(), 2);
         for item in &working_set {
             assert!(item.id > 0);
         }
     }

    #[test]
    fn test_sync_config_creation() {
        let (mut manager, _temp) = create_test_manager();
        
        let result = manager.configure_sync(
            "https://sync.example.com",
            "test-encryption-secret",
            "550e8400-e29b-41d4-a716-446655440000",
        );
        
        assert!(result.is_ok());
    }

    #[test]
    fn test_sync_with_invalid_url() {
        let (mut manager, _temp) = create_test_manager();
        
        let result = manager.configure_sync(
            "invalid-url",
            "test-encryption-secret",
            "550e8400-e29b-41d4-a716-446655440000",
        );
        
        assert!(result.is_err());
        match result {
            Err(TaskError::InvalidSyncUrl(_)) => {},
            _ => panic!("Expected InvalidSyncUrl error"),
        }
    }

    #[test]
    fn test_sync_error_handling() {
        let (mut manager, _temp) = create_test_manager();
        
        let result = manager.sync();
        
        assert!(result.is_err());
        match result {
            Err(TaskError::SyncNotConfigured) => {},
            _ => panic!("Expected SyncNotConfigured error"),
        }
    }

    #[test]
    fn test_sync_manager_creation() {
        let (mut manager, _temp) = create_test_manager();
        
        manager.configure_sync(
            "https://sync.example.com",
            "test-encryption-secret",
            "550e8400-e29b-41d4-a716-446655440000",
        ).unwrap();
        
        let result = manager.sync();
        
        assert!(result.is_ok());
        let sync_result = result.unwrap();
        assert!(!sync_result.success);
        assert!(sync_result.message.contains("Sync failed"));
    }

    #[test]
    fn test_sync_preserves_local_data_on_failure() {
        let (mut manager, _temp) = create_test_manager();
        
        let task1 = manager.create_task("Task before sync").unwrap();
        let task2 = manager.create_task("Another task").unwrap();
        
        manager.configure_sync(
            "https://nonexistent.example.com",
            "test-secret",
            "550e8400-e29b-41d4-a716-446655440000",
        ).unwrap();
        
        let _sync_result = manager.sync();
        
        let fetched1 = manager.get_task(&task1.uuid).unwrap();
        let fetched2 = manager.get_task(&task2.uuid).unwrap();
        
        assert!(fetched1.is_some());
        assert!(fetched2.is_some());
        assert_eq!(fetched1.unwrap().description, "Task before sync");
        assert_eq!(fetched2.unwrap().description, "Another task");
    }

    #[test]
    fn test_e2e_sync_roundtrip() {
        // Skip test if sync server URL not configured
        let sync_url = match std::env::var("TASKGENERAL_SYNC_URL") {
            Ok(url) if !url.is_empty() => url,
            _ => {
                eprintln!("Skipping test_e2e_sync_roundtrip: TASKGENERAL_SYNC_URL not set");
                return;
            }
        };

        // Create two separate replicas with unique data directories
        let temp_a = TestTempDir::new();
        let temp_b = TestTempDir::new();
        
        let mut replica_a = TaskManager::new(temp_a.path().to_str().unwrap()).unwrap();
        let mut replica_b = TaskManager::new(temp_b.path().to_str().unwrap()).unwrap();

        // Use same encryption secret, server, AND client_id for both replicas
        // This simulates one device syncing data, then a second device (or restored device)
        // using the same credentials to retrieve the synced data
        let shared_client_id = TcUuid::new_v4().to_string();
        let encryption_secret = "test-sync-roundtrip-secret";

        // Configure sync for both replicas
        replica_a.configure_sync(&sync_url, encryption_secret, &shared_client_id).unwrap();
        replica_b.configure_sync(&sync_url, encryption_secret, &shared_client_id).unwrap();

        // Step 1: Initial sync for both replicas to establish server connection
        let init_sync_a = replica_a.sync().unwrap();
        assert!(
            init_sync_a.success,
            "Replica A initial sync failed: {}",
            init_sync_a.message
        );

        let init_sync_b = replica_b.sync().unwrap();
        assert!(
            init_sync_b.success,
            "Replica B initial sync failed: {}",
            init_sync_b.message
        );

        // Step 2: Create a task in replica A
        let task_desc = "Roundtrip sync test task";
        let task_a = replica_a.create_task(task_desc).unwrap();
        let task_uuid = task_a.uuid.clone();

        // Verify task exists in replica A
        let fetched_a = replica_a.get_task(&task_uuid).unwrap();
        assert!(fetched_a.is_some());
        assert_eq!(fetched_a.unwrap().description, task_desc);

        // Step 3: Sync replica A to server (push the new task)
        let sync_result_a = replica_a.sync().unwrap();
        assert!(
            sync_result_a.success,
            "Replica A sync failed: {}",
            sync_result_a.message
        );

        eprintln!("Replica A synced task {}", task_uuid);

        // Step 4: Sync replica B from server (pull the new task)
        let sync_result_b = replica_b.sync().unwrap();
        assert!(
            sync_result_b.success,
            "Replica B sync failed: {}",
            sync_result_b.message
        );

        // Step 5: Verify task appears in replica B
        let fetched_b = replica_b.get_task(&task_uuid).unwrap();
        assert!(
            fetched_b.is_some(),
            "Task {} not found in replica B after sync",
            task_uuid
        );
        
        let task_b = fetched_b.unwrap();
        assert_eq!(task_b.uuid, task_uuid);
        assert_eq!(task_b.description, task_desc);
        assert_eq!(task_b.status, "pending");

        eprintln!("✓ Sync roundtrip test passed: task {} synced successfully", task_uuid);
    }

    /// Cross-device sync test: connects to the sync server using the VM's
    /// client_id and encryption_secret, syncs, and lists all tasks received.
    /// Then creates a task from this "Device B" and syncs it back.
    ///
    /// Requires env vars:
    ///   TASKGENERAL_SYNC_URL       - sync server URL (e.g. http://localhost:8443)
    ///   TASKGENERAL_VM_CLIENT_ID   - the VM's client_id UUID
    ///   TASKGENERAL_VM_SECRET      - the VM's encryption_secret
    #[test]
    fn test_cross_device_sync_with_vm() {
        let sync_url = match std::env::var("TASKGENERAL_SYNC_URL") {
            Ok(url) if !url.is_empty() => url,
            _ => {
                eprintln!("Skipping test_cross_device_sync_with_vm: TASKGENERAL_SYNC_URL not set");
                return;
            }
        };
        let client_id = match std::env::var("TASKGENERAL_VM_CLIENT_ID") {
            Ok(id) if !id.is_empty() => id,
            _ => {
                eprintln!("Skipping test_cross_device_sync_with_vm: TASKGENERAL_VM_CLIENT_ID not set");
                return;
            }
        };
        let secret = match std::env::var("TASKGENERAL_VM_SECRET") {
            Ok(s) if !s.is_empty() => s,
            _ => {
                eprintln!("Skipping test_cross_device_sync_with_vm: TASKGENERAL_VM_SECRET not set");
                return;
            }
        };

        // --- Phase 1: Pull tasks from VM (Device A) ---
        let temp_b = TestTempDir::new();
        let mut device_b = TaskManager::new(temp_b.path().to_str().unwrap()).unwrap();
        device_b.configure_sync(&sync_url, &secret, &client_id).unwrap();

        let sync_result = device_b.sync().unwrap();
        assert!(
            sync_result.success,
            "Device B sync failed: {}",
            sync_result.message
        );
        eprintln!("Device B synced successfully: {}", sync_result.message);

        let tasks = device_b.list_tasks().unwrap();
        eprintln!("Device B received {} tasks from server:", tasks.len());
        for task in &tasks {
            eprintln!(
                "  - [{}] {} (status={}, project={:?}, priority={:?})",
                task.uuid, task.description, task.status,
                task.project, task.priority
            );
        }

        // Verify we got the VM's task
        let vm_task = tasks.iter().find(|t| t.description == "Sync test from VM device A");
        assert!(
            vm_task.is_some(),
            "Expected to find 'Sync test from VM device A' in synced tasks, got: {:?}",
            tasks.iter().map(|t| &t.description).collect::<Vec<_>>()
        );
        let vm_task = vm_task.unwrap();
        assert_eq!(vm_task.status, "pending");
        assert_eq!(vm_task.priority.as_deref(), Some("H"));
        assert_eq!(vm_task.project.as_deref(), Some("test"));
        eprintln!("✓ Phase 1 PASSED: VM task received on Device B");

        // --- Phase 2: Create a task on Device B and sync back ---
        let new_task = device_b.create_task("Sync test from Rust device B").unwrap();
        let new_uuid = new_task.uuid.clone();
        eprintln!("Device B created task: {} ({})", new_task.description, new_uuid);

        let sync_result2 = device_b.sync().unwrap();
        assert!(
            sync_result2.success,
            "Device B second sync failed: {}",
            sync_result2.message
        );
        eprintln!("✓ Phase 2: Device B synced new task to server");
        eprintln!("  VM should now run 'task sync' to receive task '{}'", new_uuid);
        eprintln!("✓ Cross-device sync test PASSED");
    }

    #[test]
    fn test_set_due_date() {
        let (mut manager, _temp) = create_test_manager();
        
        let task = manager.create_task("Task with due date").unwrap();
        
        let updates = TaskUpdate {
            due: Some("2026-12-31T00:00:00Z".to_string()),
            ..Default::default()
        };
        let updated = manager.update_task(&task.uuid, updates).unwrap();
        
        assert!(updated.due.is_some());
        let due_str = updated.due.as_ref().unwrap();
        assert!(due_str.starts_with("2026-12-31"));
    }

    #[test]
    fn test_clear_due_date() {
        let (mut manager, _temp) = create_test_manager();
        
        let task = manager.create_task("Task with due date").unwrap();
        
        let updates = TaskUpdate {
            due: Some("2026-12-31T00:00:00Z".to_string()),
            ..Default::default()
        };
        manager.update_task(&task.uuid, updates).unwrap();
        
        let updates = TaskUpdate {
            due: Some("".to_string()),
            ..Default::default()
        };
        let updated = manager.update_task(&task.uuid, updates).unwrap();
        
        assert!(updated.due.is_none());
    }

    #[test]
    fn test_invalid_date() {
        let (mut manager, _temp) = create_test_manager();
        
        let task = manager.create_task("Task").unwrap();
        
        let updates = TaskUpdate {
            due: Some("not-a-date".to_string()),
            ..Default::default()
        };
        let result = manager.update_task(&task.uuid, updates);
        
        assert!(result.is_err());
        match result {
            Err(TaskError::InvalidDate(_)) => {},
            _ => panic!("Expected InvalidDate error"),
        }
    }

    #[test]
    fn test_set_wait_date() {
        let (mut manager, _temp) = create_test_manager();
        
        let task = manager.create_task("Task with wait date").unwrap();
        
        let updates = TaskUpdate {
            wait: Some("2026-06-01T00:00:00Z".to_string()),
            ..Default::default()
        };
        let updated = manager.update_task(&task.uuid, updates).unwrap();
        
        assert!(updated.wait.is_some());
        assert!(updated.is_waiting);
    }

    #[test]
    fn test_set_recurrence() {
        let (mut manager, _temp) = create_test_manager();
        
        let task = manager.create_task("Recurring task").unwrap();
        
        let updates = TaskUpdate {
            recur: Some("weekly".to_string()),
            ..Default::default()
        };
        let updated = manager.update_task(&task.uuid, updates).unwrap();
        
        assert_eq!(updated.recur, Some("weekly".to_string()));
    }

    #[test]
    fn test_clear_recurrence() {
        let (mut manager, _temp) = create_test_manager();
        
        let task = manager.create_task("Recurring task").unwrap();
        
        let updates = TaskUpdate {
            recur: Some("monthly".to_string()),
            ..Default::default()
        };
        manager.update_task(&task.uuid, updates).unwrap();
        
        let updates = TaskUpdate {
            recur: Some("".to_string()),
            ..Default::default()
        };
        let updated = manager.update_task(&task.uuid, updates).unwrap();
        
        assert!(updated.recur.is_none());
    }

    #[test]
    fn test_invalid_recurrence() {
        let (mut manager, _temp) = create_test_manager();
        
        let task = manager.create_task("Task").unwrap();
        
        let updates = TaskUpdate {
            recur: Some("invalid_value".to_string()),
            ..Default::default()
        };
        let result = manager.update_task(&task.uuid, updates);
        
        assert!(result.is_err());
        match result {
            Err(TaskError::InvalidRecurrence(_)) => {},
            _ => panic!("Expected InvalidRecurrence error"),
        }
    }

    #[test]
    fn test_start_task() {
        let (mut manager, _temp) = create_test_manager();
        
        let task = manager.create_task("Task to start").unwrap();
        
        let started = manager.start_task(&task.uuid).unwrap();
        
        assert!(started.is_active);
    }

    #[test]
    fn test_stop_task() {
        let (mut manager, _temp) = create_test_manager();
        
        let task = manager.create_task("Task to start and stop").unwrap();
        
        manager.start_task(&task.uuid).unwrap();
        let stopped = manager.stop_task(&task.uuid).unwrap();
        
        assert!(!stopped.is_active);
    }

    #[test]
    fn test_start_already_active() {
        let (mut manager, _temp) = create_test_manager();
        
        let task = manager.create_task("Task").unwrap();
        
        manager.start_task(&task.uuid).unwrap();
        let result = manager.start_task(&task.uuid);
        
        assert!(result.is_err());
        match result {
            Err(TaskError::InvalidStatus(_)) => {},
            _ => panic!("Expected InvalidStatus error"),
        }
    }

    #[test]
    fn test_stop_not_active() {
        let (mut manager, _temp) = create_test_manager();
        
        let task = manager.create_task("Task").unwrap();
        
        let result = manager.stop_task(&task.uuid);
        
        assert!(result.is_err());
        match result {
            Err(TaskError::InvalidStatus(_)) => {},
            _ => panic!("Expected InvalidStatus error"),
        }
    }

    #[test]
    fn test_urgency_bare_task() {
        let (mut manager, _temp) = create_test_manager();
        
        let task = manager.create_task("Bare task").unwrap();
        
        assert!(task.urgency >= 0.0);
    }

    #[test]
    fn test_urgency_high_priority() {
        let (mut manager, _temp) = create_test_manager();
        
        let task = manager.create_task("High priority task").unwrap();
        
        let updates = TaskUpdate {
            priority: Some("H".to_string()),
            ..Default::default()
        };
        let updated = manager.update_task(&task.uuid, updates).unwrap();
        
        assert!(updated.urgency >= 6.0);
    }

    #[test]
    fn test_urgency_active_task() {
        let (mut manager, _temp) = create_test_manager();
        
        let task = manager.create_task("Task to start").unwrap();
        
        let started = manager.start_task(&task.uuid).unwrap();
        
        assert!(started.urgency >= 4.0);
    }

    #[test]
    fn test_urgency_overdue_task() {
        let (mut manager, _temp) = create_test_manager();
        
        let task = manager.create_task("Overdue task").unwrap();
        
        let updates = TaskUpdate {
            due: Some("2020-01-01T00:00:00Z".to_string()),
            ..Default::default()
        };
        let updated = manager.update_task(&task.uuid, updates).unwrap();
        
        assert!(updated.urgency >= 12.0);
    }

    #[test]
    fn test_urgency_waiting_negative() {
        let (mut manager, _temp) = create_test_manager();
        
        let task = manager.create_task("Waiting task").unwrap();
        
        let updates = TaskUpdate {
            wait: Some("2099-01-01T00:00:00Z".to_string()),
            ..Default::default()
        };
        let updated = manager.update_task(&task.uuid, updates).unwrap();
        
        assert!(updated.urgency <= 0.0);
    }

    #[test]
    fn test_sort_by_urgency() {
        let (mut manager, _temp) = create_test_manager();
        
        let task_h = manager.create_task("High priority task").unwrap();
        let updates = TaskUpdate {
            priority: Some("H".to_string()),
            ..Default::default()
        };
        manager.update_task(&task_h.uuid, updates).unwrap();
        
        let task_l = manager.create_task("Low priority task").unwrap();
        let updates = TaskUpdate {
            priority: Some("L".to_string()),
            ..Default::default()
        };
        manager.update_task(&task_l.uuid, updates).unwrap();
        
        manager.create_task("Bare task").unwrap();
        
        let sorted = manager.list_tasks_sorted(TaskFilter::default(), SortField::Urgency).unwrap();
        
        assert_eq!(sorted.len(), 3);
        assert_eq!(sorted[0].uuid, task_h.uuid);
        assert!(sorted[0].urgency > sorted[1].urgency);
        assert!(sorted[1].urgency > sorted[2].urgency);
    }

    #[test]
    fn test_sort_by_description() {
        let (mut manager, _temp) = create_test_manager();
        
        manager.create_task("Charlie").unwrap();
        manager.create_task("Alice").unwrap();
        manager.create_task("Bob").unwrap();
        
        let sorted = manager.list_tasks_sorted(TaskFilter::default(), SortField::Description).unwrap();
        
        assert_eq!(sorted.len(), 3);
        assert_eq!(sorted[0].description, "Alice");
        assert_eq!(sorted[1].description, "Bob");
        assert_eq!(sorted[2].description, "Charlie");
    }

    #[test]
    fn test_waiting_tasks_auto_hidden() {
        let (mut manager, _temp) = create_test_manager();
        
        let waiting_task = manager.create_task("Waiting task").unwrap();
        let updates = TaskUpdate {
            wait: Some("2099-01-01T00:00:00Z".to_string()),
            ..Default::default()
        };
        let updated = manager.update_task(&waiting_task.uuid, updates).unwrap();
        assert!(updated.is_waiting);
        
        manager.create_task("Normal task").unwrap();
        
        let default_list = manager.list_tasks_sorted(TaskFilter::default(), SortField::Urgency).unwrap();
        
        assert_eq!(default_list.len(), 1);
        assert_eq!(default_list[0].description, "Normal task");
        
        let all_tasks = manager.list_tasks().unwrap();
        assert_eq!(all_tasks.len(), 2);
        
        let waiting_in_all = all_tasks.iter().find(|t| t.uuid == waiting_task.uuid);
        assert!(waiting_in_all.is_some());
        assert!(waiting_in_all.unwrap().is_waiting);
    }
}
