package dev.rohans.taskwarrior.navigation

import org.junit.Test
import org.junit.Assert.*

/**
 * TDD: Navigation tests - RED phase
 * These tests verify navigation routes and routing logic
 */
class NavigationTest {

    @Test
    fun test_navigation_to_task_list() {
        // Verify default route is task_list
        val route = Route.TaskList.route
        assertEquals("task_list", route)
    }

    @Test
    fun test_navigation_to_task_edit_new() {
        // Verify new task route
        val route = Route.TaskEdit.createRoute(null)
        assertEquals("task_edit/new", route)
    }

    @Test
    fun test_navigation_to_task_edit_existing() {
        // Verify edit task route with UUID
        val uuid = "test-uuid-123"
        val route = Route.TaskEdit.createRoute(uuid)
        assertEquals("task_edit/$uuid", route)
    }

    @Test
    fun test_navigation_to_sync_settings() {
        // Verify sync settings route
        val route = Route.SyncSettings.route
        assertEquals("sync_settings", route)
    }

    @Test
    fun test_route_pattern_for_task_edit() {
        // Verify route pattern includes path parameter
        val pattern = Route.TaskEdit.route
        assertEquals("task_edit/{uuid}", pattern)
    }

    @Test
    fun test_all_routes_defined() {
        // Verify all routes are accessible
        val routes = listOf(
            Route.TaskList.route,
            Route.TaskEdit.route,
            Route.SyncSettings.route
        )
        
        assertEquals(3, routes.size)
        assertTrue(routes.contains("task_list"))
        assertTrue(routes.contains("task_edit/{uuid}"))
        assertTrue(routes.contains("sync_settings"))
    }
}
