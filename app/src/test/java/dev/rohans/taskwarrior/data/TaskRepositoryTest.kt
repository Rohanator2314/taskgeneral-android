package dev.rohans.taskwarrior.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uniffi.taskgeneral_core.TaskException

class TaskRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testErrorPropagation_EmptyDescription() = runTest {
        val dataDir = tempFolder.newFolder("taskgeneral-test")
        val repository = TaskRepository(dataDir)

        try {
            repository.createTask("")
            fail("Expected TaskException for empty description")
        } catch (e: TaskException.InvalidDescription) {
            assertTrue("Error message should mention empty or description", 
                e.message?.contains("empty", ignoreCase = true) == true ||
                e.message?.contains("description", ignoreCase = true) == true)
        }
    }

    @Test
    fun testBasicTaskCreation() = runTest {
        val dataDir = tempFolder.newFolder("taskgeneral-test-2")
        val repository = TaskRepository(dataDir)

        val task = repository.createTask("Test task")
        
        assertEquals("Test task", task.description)
        assertEquals("pending", task.status)
        assertNotNull(task.uuid)
    }

    @Test
    fun testTaskRetrieval() = runTest {
        val dataDir = tempFolder.newFolder("taskgeneral-test-3")
        val repository = TaskRepository(dataDir)

        val created = repository.createTask("Another test task")
        val retrieved = repository.getTask(created.uuid)

        assertNotNull(retrieved)
        assertEquals(created.uuid, retrieved?.uuid)
        assertEquals("Another test task", retrieved?.description)
    }
}
