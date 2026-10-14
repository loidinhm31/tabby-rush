package dev.doggo.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.contract.AuthState
import dev.doggo.shared.contract.Todo
import dev.doggo.shared.contract.TodoAction
import dev.doggo.shared.contract.TodoFilter
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
    private lateinit var app: DoggoApplication
    private val testAccountKey = AccountKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        app = DoggoApplication.instance
        app.accountFiles.deleteDatabaseAndArtifacts(testAccountKey)
        app.vault.clear(testAccountKey)
        app.accountFiles.clearWipeMarker(testAccountKey)
    }

    @After
    fun tearDown() = runBlocking {
        app.closeActiveRuntime()
        app.accountFiles.deleteDatabaseAndArtifacts(testAccountKey)
        app.vault.clear(testAccountKey)
        app.accountFiles.clearWipeMarker(testAccountKey)
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
        kotlinx.coroutines.delay(50)
        assertEquals(TodoFilter.ACTIVE, viewModel.uiState.value.filter)

        viewModel.onAction(TodoAction.SelectFilter(TodoFilter.COMPLETED))
        kotlinx.coroutines.delay(50)
        assertEquals(TodoFilter.COMPLETED, viewModel.uiState.value.filter)
    }

    @Test
    fun testEditorOpenAndCancel() = runBlocking {
        val viewModel = SessionViewModel()
        viewModel.onAction(TodoAction.OpenCreate("Initial title"))
        kotlinx.coroutines.delay(50)
        val stateWithEditor = viewModel.uiState.value
        assertNotNull(stateWithEditor.editor)
        assertEquals("Initial title", stateWithEditor.editor?.rawTitle)

        viewModel.onAction(TodoAction.CancelEditor)
        kotlinx.coroutines.delay(50)
        assertEquals(null, viewModel.uiState.value.editor)
    }

    @Test
    fun testAccountRuntimeCreationAndStoreAccess() = runBlocking {
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
    }
}
