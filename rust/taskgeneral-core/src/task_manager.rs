use crate::error::{Result, TaskError};
use crate::models::{SyncResult, TaskFilter, TaskInfo, TaskUpdate};
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
            let storage = SqliteStorage::new(path, AccessMode::ReadWrite, true).await?;
            Ok::<Replica<SqliteStorage>, taskchampion::Error>(Replica::new(storage))
        })?;

        Ok(TaskManager { 
            replica, 
            runtime,
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

    pub fn get_working_set(&mut self) -> Result<Vec<(usize, TaskInfo)>> {
        self.runtime.block_on(async {
            let working_set = self.replica.working_set().await?;
            let mut result = Vec::new();

            for (id, uuid) in working_set.iter() {
                if let Some(task) = self.replica.get_task(uuid).await? {
                    result.push((id, task_to_info(&task)));
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
        
        let pending_tasks: Vec<_> = working_set.iter().filter(|(_, t)| t.status == "pending").collect();
        assert_eq!(pending_tasks.len(), 2);
        for (id, _task) in &working_set {
            assert!(id > &0);
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
}
