package dev.doggo.desktop

import dev.doggo.shared.contract.BuildMode
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
        val lockFile = File.createTempFile("doggo_ctrl_test", ".lock")
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
        val lockFile = File.createTempFile("doggo_ctrl_rel_test", ".lock")
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

        val lockFile = File.createTempFile("doggo_ctrl_fallback_test", ".lock")
        lockFile.deleteOnExit()
        val guard = SingleInstanceGuard.tryAcquire(lockFile)
        assertNotNull(guard)

        val origProp = System.getProperty("doggo.env.file")
        try {
            System.setProperty("doggo.env.file", emptyEnv.absolutePath)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val controller = DesktopController(BuildMode.DEBUG, scope, guard)
            try {
                assertEquals("http://127.0.0.1:3000", controller.hubConfig.canonicalOrigin)
                assertEquals("", controller.hubConfig.appApiKey)
            } finally {
                controller.dispose()
            }
        } finally {
            if (origProp != null) System.setProperty("doggo.env.file", origProp) else System.clearProperty("doggo.env.file")
        }
    }
}
