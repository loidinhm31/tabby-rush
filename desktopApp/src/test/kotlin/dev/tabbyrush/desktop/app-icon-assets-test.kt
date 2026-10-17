package dev.tabbyrush.desktop

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AppIconAssetsTest {

    @Test
    fun testDesktopRuntimeIconResolvesOnClasspathWithoutFallback() {
        val stream = checkNotNull(
            javaClass.classLoader.getResourceAsStream("tabbyrush-icon.png")
        ) { "tabbyrush-icon.png must resolve directly on classpath without filesystem fallback" }

        val image = stream.use { ImageIO.read(it) }
        assertNotNull(image, "tabbyrush-icon.png must be decodable by ImageIO")
        assertEquals(256, image.width, "Runtime icon width must be 256px")
        assertEquals(256, image.height, "Runtime icon height must be 256px")
    }

    @Test
    fun testTrayIconResolvesOnClasspath() {
        val stream = checkNotNull(
            javaClass.classLoader.getResourceAsStream("task-surface-tray.png")
        ) { "task-surface-tray.png must resolve directly on classpath" }

        val image = stream.use { ImageIO.read(it) }
        assertNotNull(image, "task-surface-tray.png must be decodable by ImageIO")
        assertEquals(32, image.width, "Tray icon width must be 32px")
        assertEquals(32, image.height, "Tray icon height must be 32px")
    }

    @Test
    fun testLinuxAndWindowsTrayAssetsBothExistAndDecode() {
        val linuxTray = File("src/linuxMain/resources/task-surface-tray.png")
        val windowsTray = File("src/windowsMain/resources/task-surface-tray.png")

        assertTrue(linuxTray.exists(), "Linux tray icon must exist at $linuxTray")
        assertTrue(windowsTray.exists(), "Windows tray icon must exist at $windowsTray")

        val linuxImg = linuxTray.inputStream().use { ImageIO.read(it) }
        val windowsImg = windowsTray.inputStream().use { ImageIO.read(it) }

        assertNotNull(linuxImg, "Linux tray icon must decode")
        assertNotNull(windowsImg, "Windows tray icon must decode")

        assertEquals(32, linuxImg.width)
        assertEquals(32, linuxImg.height)
        assertEquals(32, windowsImg.width)
        assertEquals(32, windowsImg.height)
    }

    @Test
    fun testWindowsIcoFileContainsAllDocumentedResolutionsAndDecodes() {
        val icoFile = File("src/windowsMain/resources/tabbyrush.ico")
        assertTrue(icoFile.exists(), "tabbyrush.ico must exist in windowsMain resources")
        val bytes = icoFile.readBytes()
        assertTrue(bytes.size > 6, "tabbyrush.ico must have header bytes")

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val reserved = buffer.short.toInt() and 0xFFFF
        val type = buffer.short.toInt() and 0xFFFF
        val count = buffer.short.toInt() and 0xFFFF

        assertEquals(0, reserved, "ICO reserved bytes must be 0")
        assertEquals(1, type, "ICO image type must be 1 (icon)")
        assertEquals(7, count, "ICO must contain exactly 7 embedded resolution frames")

        val expectedSizes = setOf(16, 24, 32, 48, 64, 128, 256)
        val parsedSizes = mutableSetOf<Int>()

        for (i in 0 until count) {
            val offset = 6 + i * 16
            val rawW = bytes[offset].toInt() and 0xFF
            val rawH = bytes[offset + 1].toInt() and 0xFF
            val w = if (rawW == 0) 256 else rawW
            val h = if (rawH == 0) 256 else rawH

            buffer.position(offset + 6)
            val bpp = buffer.short.toInt() and 0xFFFF
            val imgSize = buffer.int
            val imgOffset = buffer.int

            assertEquals(w, h, "ICO frame must be square")
            assertEquals(32, bpp, "ICO frame must be 32-bit RGBA")
            assertTrue(imgOffset > 0 && imgOffset + imgSize <= bytes.size, "Image frame offset within file bounds")

            parsedSizes.add(w)

            // Verify embedded PNG frame decodes cleanly with ImageIO
            val frameBytes = bytes.copyOfRange(imgOffset, imgOffset + imgSize)
            val decodedFrame = ByteArrayInputStream(frameBytes).use { ImageIO.read(it) }
            assertNotNull(decodedFrame, "Embedded frame ${w}x$h must decode with ImageIO")
            assertEquals(w, decodedFrame.width, "Decoded width must match directory header")
            assertEquals(h, decodedFrame.height, "Decoded height must match directory header")
        }

        assertEquals(expectedSizes, parsedSizes, "ICO must contain all documented sizes [16, 24, 32, 48, 64, 128, 256]")
    }
}
