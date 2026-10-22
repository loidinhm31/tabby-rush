package dev.tabbyrush.shared.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.Todo
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidRoomAccountStoreTest {

    private lateinit var context: Context
    private lateinit var accountFiles: AndroidAccountFiles
    private lateinit var store: RoomAccountStore
    private lateinit var database: TabbyRushDatabase

    private val accountKey = AccountKey.create("http://localhost:3000", "user-android-instrumented")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        accountFiles = AndroidAccountFiles(context)

        val dbFile = accountFiles.getDatabaseFile(accountKey)
        database = createAndroidDatabaseBuilder(context, dbFile).build()
        store = RoomAccountStore(
            accountKey = accountKey,
            userId = "user-android-instrumented",
            database = database,
            appId = "tabby-rush"
        )
    }
    private fun taskRef(id: String) = dev.tabbyrush.shared.contract.TaskRef("user-android-instrumented", id)

    @After
    fun tearDown() {
        store.close()
        accountFiles.deleteDatabaseAndArtifacts(accountKey)
        accountFiles.clearWipeMarker(accountKey)
    }

    @Test
    fun testSaveAndLoadTodosOnAndroid() = runBlocking {
        val now = System.currentTimeMillis()
        val todo = Todo(
            taskRef = taskRef("android-todo-1"),
            title = "Test Android Room",
            completed = false,
            createdAt = now,
            updatedAt = now,
            serverRevision = 0L
        )

        store.saveTodoLocally(todo)

        val loaded = store.loadTodos()
        assertEquals(1, loaded.size)
        assertEquals("android-todo-1", loaded[0].id)
        assertEquals("Test Android Room", loaded[0].title)
        assertTrue(loaded[0].isPending)
    }

    @Test
    fun testUnsentCreateDeleteCancelsLocallyOnAndroid() = runBlocking {
        val now = System.currentTimeMillis()
        val todo = Todo(
            taskRef = taskRef("android-todo-cancel"),
            title = "Cancel Me",
            completed = false,
            createdAt = now,
            updatedAt = now
        )

        store.saveTodoLocally(todo)
        assertEquals(1, store.dao.countDrafts())
        assertEquals(1, store.dao.countOutbox())

        store.deleteTodoLocally(taskRef("android-todo-cancel"))
        assertEquals(0, store.dao.countDrafts())
        assertEquals(0, store.dao.countOutbox())
        assertEquals(0, store.loadTodos().size)
    }

    @Test
    fun testWipeMarkerPersistenceOnAndroid() {
        assertFalse(accountFiles.hasPendingWipe(accountKey))

        accountFiles.writeWipeMarker(accountKey, 100L)
        assertTrue(accountFiles.hasPendingWipe(accountKey))

        accountFiles.clearWipeMarker(accountKey)
        assertFalse(accountFiles.hasPendingWipe(accountKey))
    }

    @Test
    fun testDeleteDatabaseArtifactsOnAndroid() = runBlocking {
        val now = System.currentTimeMillis()
        store.saveTodoLocally(
            Todo(
                taskRef = taskRef("android-del-1"),
                title = "Delete Me",
                completed = false,
                createdAt = now,
                updatedAt = now
            )
        )

        store.close()

        val deleted = accountFiles.deleteDatabaseAndArtifacts(accountKey)
        assertTrue(deleted)

        val dbFile = accountFiles.getDatabaseFile(accountKey)
        assertFalse(dbFile.exists())
    }
}
