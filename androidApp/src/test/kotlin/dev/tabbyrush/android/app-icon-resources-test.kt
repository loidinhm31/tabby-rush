package dev.tabbyrush.android

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppIconResourcesTest {

    @Test
    fun testAdaptiveLauncherIconResourcesExist() {
        val launcherXml = File("src/main/res/mipmap-anydpi-v26/ic_launcher.xml")
        val launcherRoundXml = File("src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml")
        val bgXml = File("src/main/res/drawable/ic_launcher_background.xml")
        val fgPng = File("src/main/res/drawable/ic_launcher_foreground.png")

        assertTrue(launcherXml.exists(), "ic_launcher.xml must exist under mipmap-anydpi-v26")
        assertTrue(launcherRoundXml.exists(), "ic_launcher_round.xml must exist under mipmap-anydpi-v26")
        assertTrue(bgXml.exists(), "ic_launcher_background.xml must exist under drawable")
        assertTrue(fgPng.exists(), "ic_launcher_foreground.png must exist under drawable")

        // Validate adaptive XML references
        val launcherContent = launcherXml.readText()
        assertTrue(launcherContent.contains("<adaptive-icon"), "ic_launcher.xml must declare <adaptive-icon>")
        assertTrue(launcherContent.contains("@drawable/ic_launcher_background"), "ic_launcher.xml must reference background")
        assertTrue(launcherContent.contains("@drawable/ic_launcher_foreground"), "ic_launcher.xml must reference foreground")

        // Parse PNG IHDR chunk (bytes 16-24) to validate dimensions without desktop AWT dependencies
        val bytes = fgPng.readBytes()
        assertTrue(bytes.size > 24, "PNG file must be larger than 24 bytes")
        // Check PNG signature: 0x89 'P' 'N' 'G'
        assertEquals(0x89.toByte(), bytes[0])
        assertEquals('P'.code.toByte(), bytes[1])
        assertEquals('N'.code.toByte(), bytes[2])
        assertEquals('G'.code.toByte(), bytes[3])

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        buffer.position(16)
        val width = buffer.int
        val height = buffer.int

        assertEquals(432, width, "Foreground width must be 432px (4x 108dp canvas)")
        assertEquals(432, height, "Foreground height must be 432px (4x 108dp canvas)")
    }

    @Test
    fun testManifestReferencesAdaptiveIcons() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        assertTrue(manifestFile.exists(), "AndroidManifest.xml must exist")
        val manifestContent = manifestFile.readText()

        assertTrue(
            manifestContent.contains("android:icon=\"@mipmap/ic_launcher\""),
            "Manifest must declare android:icon=\"@mipmap/ic_launcher\""
        )
        assertTrue(
            manifestContent.contains("android:roundIcon=\"@mipmap/ic_launcher_round\""),
            "Manifest must declare android:roundIcon=\"@mipmap/ic_launcher_round\""
        )
    }
}
