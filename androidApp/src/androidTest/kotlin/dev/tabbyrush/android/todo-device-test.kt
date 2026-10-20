package dev.tabbyrush.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.AuthState
import dev.tabbyrush.shared.contract.Todo
import dev.tabbyrush.shared.contract.TodoAction
import dev.tabbyrush.shared.contract.TodoFilter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodoDeviceTest {

    private lateinit var context: Context
    private lateinit var app: TabbyRushApplication
    private val canonicalIdentity = dev.tabbyrush.shared.contract.AccountIdentity("test-user-id", "testuser", "test@example.com")
    private val testAccountKey by lazy { AccountKey.create(app.hubConfig.canonicalOrigin, canonicalIdentity.userId) }

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        app = TabbyRushApplication.instance
        app.closeActiveRuntime()
        app.clearActiveAccount()
        app.accountFiles.deleteDatabaseAndArtifacts(testAccountKey)
        app.vault.clear(testAccountKey)
        app.accountFiles.clearWipeMarker(testAccountKey)
    }

    @After
    fun tearDown() = runBlocking {
        app.closeActiveRuntime()
        app.clearActiveAccount()
        app.accountFiles.deleteDatabaseAndArtifacts(testAccountKey)
        app.vault.clear(testAccountKey)
        app.accountFiles.clearWipeMarker(testAccountKey)
    }

    private suspend fun awaitUiState(
        viewModel: SessionViewModel,
        timeoutMs: Long = 2_000L,
        condition: (dev.tabbyrush.shared.contract.TodoUiState) -> Boolean
    ) {
        val start = System.currentTimeMillis()
        while (!condition(viewModel.uiState.value) && (System.currentTimeMillis() - start) < timeoutMs) {
            kotlinx.coroutines.delay(20)
        }
        org.junit.Assert.assertTrue("UiState condition timed out after ${timeoutMs}ms", condition(viewModel.uiState.value))
    }

    @Test
    fun testInitialUiState() {
        val viewModel = SessionViewModel()
        val state = viewModel.uiState.value
        assertNotNull("UiState should not be null", state)
        assertEquals(AuthState.UNAUTHENTICATED, state.authState)
        assertEquals(0, state.items.size)
        assertEquals(TodoFilter.ALL, state.filter)
    }

    @Test
    fun testFilterSelectionAction() = runBlocking {
        val viewModel = SessionViewModel()
        viewModel.onAction(TodoAction.SelectFilter(TodoFilter.ACTIVE))
        awaitUiState(viewModel) { it.filter == TodoFilter.ACTIVE }
        assertEquals(TodoFilter.ACTIVE, viewModel.uiState.value.filter)

        viewModel.onAction(TodoAction.SelectFilter(TodoFilter.COMPLETED))
        awaitUiState(viewModel) { it.filter == TodoFilter.COMPLETED }
        assertEquals(TodoFilter.COMPLETED, viewModel.uiState.value.filter)
    }

    @Test
    fun testEditorOpenAndCancel() = runBlocking {
        val viewModel = SessionViewModel()
        viewModel.onAction(TodoAction.OpenCreate("Initial title"))
        awaitUiState(viewModel) { it.editor != null }
        val stateWithEditor = viewModel.uiState.value
        assertNotNull(stateWithEditor.editor)
        assertEquals("Initial title", stateWithEditor.editor?.rawTitle)

        viewModel.onAction(TodoAction.CancelEditor)
        awaitUiState(viewModel) { it.editor == null }
        assertEquals(null, viewModel.uiState.value.editor)
    }

    @Test
    fun testAccountRuntimeCreationAndStoreAccess() = runBlocking {
        app.saveActiveAccount(canonicalIdentity, testAccountKey)
        try {
            val runtime = app.getOrCreateAccountRuntime(testAccountKey)
            assertNotNull(runtime)
            assertEquals(testAccountKey, runtime.accountKey)
            assertNotNull(runtime.store)
            assertNotNull(runtime.coordinator)

            val now = System.currentTimeMillis()
            val todo = Todo(id = "todo_device_1", title = "Test device task", completed = false, createdAt = now, updatedAt = now)
            runtime.store.saveTodoLocally(todo)

            val todos = runtime.store.loadTodos()
            assertEquals(1, todos.size)
            assertEquals("Test device task", todos[0].title)
        } finally {
            app.closeActiveRuntime()
            app.clearActiveAccount()
            app.accountFiles.deleteDatabaseAndArtifacts(testAccountKey)
        }
    }
}
