package dev.tabbyrush.desktop

import dev.tabbyrush.shared.contract.BuildMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DesktopControllerEnvTest {

    @Test
    fun testDesktopControllerLoadsCredentialsFromDotEnv() {
        val lockFile = File.createTempFile("tabbyrush_ctrl_test", ".lock")
        lockFile.deleteOnExit()
        val guard = SingleInstanceGuard.tryAcquire(lockFile)
        assertNotNull(guard)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = DesktopController(BuildMode.DEBUG, scope, guard)

        try {
            assertEquals("https://api-glean-hub.qm-hub-v001.cloud", controller.hubConfig.canonicalOrigin)
            assertEquals("XoaJSoximq8EhK06IjXAQCOqKqRtM6qy", controller.hubConfig.appApiKey)
            assertEquals(BuildMode.DEBUG, controller.hubConfig.buildMode)
        } finally {
            controller.dispose()
        }
    }

    @Test
    fun testDesktopControllerReleaseModeLoadsCredentialsFromDotEnv() {
        val lockFile = File.createTempFile("tabbyrush_ctrl_rel_test", ".lock")
        lockFile.deleteOnExit()
        val guard = SingleInstanceGuard.tryAcquire(lockFile)
        assertNotNull(guard)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = DesktopController(BuildMode.RELEASE, scope, guard)

        try {
            assertEquals("https://api-glean-hub.qm-hub-v001.cloud", controller.hubConfig.canonicalOrigin)
            assertEquals("XoaJSoximq8EhK06IjXAQCOqKqRtM6qy", controller.hubConfig.appApiKey)
            assertEquals(BuildMode.RELEASE, controller.hubConfig.buildMode)
        } finally {
            controller.dispose()
        }
    }

    @Test
    fun testDesktopControllerWithoutEnvFallsBackToLoopbackInDebug() {
        val emptyEnv = File.createTempFile("empty-env", ".env")
        emptyEnv.deleteOnExit()
        emptyEnv.writeText("")

        val lockFile = File.createTempFile("tabbyrush_ctrl_fallback_test", ".lock")
        lockFile.deleteOnExit()
        val guard = SingleInstanceGuard.tryAcquire(lockFile)
        assertNotNull(guard)

        val origProp = System.getProperty("tabbyrush.env.file")
        try {
            System.setProperty("tabbyrush.env.file", emptyEnv.absolutePath)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val controller = DesktopController(BuildMode.DEBUG, scope, guard)
            try {
                val expectedOrigin = System.getenv("APPROVED_HUB_ORIGIN")?.takeIf { it.isNotBlank() } ?: "http://127.0.0.1:3000"
                val expectedApiKey = System.getenv("TABBYRUSH_API_KEY")?.takeIf { it.isNotBlank() } ?: ""
                assertEquals(expectedOrigin, controller.hubConfig.canonicalOrigin)
                assertEquals(expectedApiKey, controller.hubConfig.appApiKey)
            } finally {
                controller.dispose()
            }
        } finally {
            if (origProp != null) System.setProperty("tabbyrush.env.file", origProp) else System.clearProperty("tabbyrush.env.file")
        }
    }

    @Test
    fun testDesktopControllerTriggerSyncUpdatesProgressFlow() {
        val lockFile = File.createTempFile("tabbyrush_ctrl_sync_test", ".lock")
        lockFile.deleteOnExit()
        val guard = SingleInstanceGuard.tryAcquire(lockFile)
        assertNotNull(guard)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = DesktopController(BuildMode.DEBUG, scope, guard)

        try {
            controller.onAction(dev.tabbyrush.shared.contract.TodoAction.TriggerSync)
            assertNotNull(controller.uiState.value.syncProgress)
        } finally {
            controller.dispose()
        }
    }
}
