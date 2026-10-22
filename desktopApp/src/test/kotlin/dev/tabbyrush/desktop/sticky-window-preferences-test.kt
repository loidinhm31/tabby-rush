package dev.tabbyrush.desktop

import java.io.File
import java.nio.file.Files
import java.util.Collections
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StickyWindowPreferencesTest {

    private lateinit var tempDir: File
    private lateinit var prefsFile: File
    private lateinit var preferences: StickyWindowPreferences

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("sticky-prefs-test").toFile()
        prefsFile = File(tempDir, "sticky-window.properties")
        preferences = StickyWindowPreferences(prefsFile)
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testMissingFileReturnsDefaultHiddenPresentation() {
        assertFalse(prefsFile.exists())
        val presentation = preferences.load()

        assertFalse(presentation.visible)
        assertEquals(0.0, presentation.geometry.xDp)
        assertEquals(0.0, presentation.geometry.yDp)
        assertEquals(DEFAULT_STICKY_WIDTH_DP, presentation.geometry.widthDp)
        assertEquals(DEFAULT_STICKY_HEIGHT_DP, presentation.geometry.heightDp)
        assertEquals(1.0, presentation.geometry.savedScaleX)
        assertEquals(1.0, presentation.geometry.savedScaleY)
        assertEquals(DEFAULT_STICKY_FONT_SCALE, presentation.fontScale)
    }

    @Test
    fun testRoundTripPersistence() {
        val original = StickyWindowPresentation(
            visible = true,
            geometry = StickyWindowGeometry(
                xDp = 150.0,
                yDp = 250.0,
                widthDp = 400.0,
                heightDp = 600.0,
                savedScaleX = 1.25,
                savedScaleY = 1.25
            )
        )

        preferences.save(original)
        assertTrue(prefsFile.exists())

        val loaded = preferences.load()
        assertEquals(original.visible, loaded.visible)
        assertEquals(original.geometry.xDp, loaded.geometry.xDp)
        assertEquals(original.geometry.yDp, loaded.geometry.yDp)
        assertEquals(original.geometry.widthDp, loaded.geometry.widthDp)
        assertEquals(original.geometry.heightDp, loaded.geometry.heightDp)
        assertEquals(original.geometry.savedScaleX, loaded.geometry.savedScaleX)
        assertEquals(original.geometry.savedScaleY, loaded.geometry.savedScaleY)
        assertEquals(original.fontScale, loaded.fontScale)
    }

    @Test
    fun testCorruptFileContentFallsBackToDefaults() {
        prefsFile.writeText("THIS IS NOT A VALID PROPERTIES FILE\n===###$$$\nversion=NaN\nxDp=foo")

        val loaded = preferences.load()
        assertFalse(loaded.visible)
        assertEquals(DEFAULT_STICKY_WIDTH_DP, loaded.geometry.widthDp)
        assertEquals(DEFAULT_STICKY_HEIGHT_DP, loaded.geometry.heightDp)
        assertEquals(DEFAULT_STICKY_FONT_SCALE, loaded.fontScale)
    }

    @Test
    fun testNegativeOrInfiniteDimensionsRecoverToDefaults() {
        prefsFile.writeText(
            """
            version=1
            visible=true
            xDp=0.0
            yDp=0.0
            widthDp=-360.0
            heightDp=Infinity
            savedScaleX=1.0
            savedScaleY=1.0
            """.trimIndent()
        )

        val loaded = preferences.load()
        // Visibility retained if parsed, but geometry falls back to safe defaults
        assertTrue(loaded.visible)
        assertEquals(DEFAULT_STICKY_WIDTH_DP, loaded.geometry.widthDp)
        assertEquals(DEFAULT_STICKY_HEIGHT_DP, loaded.geometry.heightDp)
        assertEquals(1.0, loaded.geometry.savedScaleX)
    }

    @Test
    fun testFontScaleRoundTripAndBounds() {
        // 1. Explicit valid fontScale round-trip
        val presWithFont = StickyWindowPresentation(
            visible = true,
            geometry = StickyWindowGeometry(10.0, 20.0, 360.0, 520.0, 1.0, 1.0),
            fontScale = 0.625f
        )
        preferences.save(presWithFont)
        val loaded = preferences.load()
        assertEquals(0.625f, loaded.fontScale)
        assertTrue(loaded.visible)
        assertEquals(360.0, loaded.geometry.widthDp)
        // 2. Legacy properties file with no fontScale falls back to default 1.0f
        prefsFile.writeText(
            """
            version=1
            visible=true
            xDp=50.0
            yDp=50.0
            widthDp=380.0
            heightDp=500.0
            savedScaleX=1.0
            savedScaleY=1.0
            """.trimIndent()
        )
        val legacyLoaded = preferences.load()
        assertEquals(DEFAULT_STICKY_FONT_SCALE, legacyLoaded.fontScale)
        assertEquals(380.0, legacyLoaded.geometry.widthDp)
        assertTrue(legacyLoaded.visible)

        // 3. Out-of-bounds fontScale (< 0.50 or > 1.50) recovers to default without corrupting geometry
        prefsFile.writeText(
            """
            version=1
            visible=true
            xDp=50.0
            yDp=50.0
            widthDp=380.0
            heightDp=500.0
            savedScaleX=1.0
            savedScaleY=1.0
            fontScale=0.40
            """.trimIndent()
        )
        val outOfBoundsLoaded = preferences.load()
        assertEquals(DEFAULT_STICKY_FONT_SCALE, outOfBoundsLoaded.fontScale)
        assertEquals(380.0, outOfBoundsLoaded.geometry.widthDp)
        assertTrue(outOfBoundsLoaded.visible)

        // 4. Non-finite values (NaN, Infinity) recover to default
        for (nonFinite in listOf("NaN", "Infinity", "-Infinity")) {
            prefsFile.writeText(
                """
                version=1
                visible=true
                xDp=50.0
                yDp=50.0
                widthDp=380.0
                heightDp=500.0
                savedScaleX=1.0
                savedScaleY=1.0
                fontScale=$nonFinite
                """.trimIndent()
            )
            val nonFiniteLoaded = preferences.load()
            assertEquals(DEFAULT_STICKY_FONT_SCALE, nonFiniteLoaded.fontScale)
            assertEquals(380.0, nonFiniteLoaded.geometry.widthDp)
        }

        // 5. Property alias sticky.window.font_scale is supported
        prefsFile.writeText(
            """
            version=1
            visible=true
            xDp=50.0
            yDp=50.0
            widthDp=380.0
            heightDp=500.0
            savedScaleX=1.0
            savedScaleY=1.0
            sticky.window.font_scale=1.25
            """.trimIndent()
        )
        val aliasLoaded = preferences.load()
        assertEquals(1.25f, aliasLoaded.fontScale)
    }

    @Test
    fun testAutoCreatesParentDirectories() {
        val nestedDir = File(tempDir, "nested/config/sub")
        val nestedFile = File(nestedDir, "sticky-window.properties")
        val nestedPrefs = StickyWindowPreferences(nestedFile)

        assertFalse(nestedDir.exists())
        nestedPrefs.save(
            StickyWindowPresentation(
                visible = false,
                geometry = StickyWindowGeometry(10.0, 20.0, 360.0, 520.0, 1.0, 1.0)
            )
        )

        assertTrue(nestedFile.exists())
        val loaded = nestedPrefs.load()
        assertEquals(10.0, loaded.geometry.xDp)
    }

    @Test
    fun testTemporaryFilesCleanedUpAfterSave() {
        val presentation = StickyWindowPresentation(
            visible = true,
            geometry = StickyWindowGeometry(50.0, 50.0, 360.0, 520.0, 1.0, 1.0)
        )
        preferences.save(presentation)

        val tmpFiles = tempDir.listFiles { _, name -> name.endsWith(".tmp") } ?: emptyArray()
        assertEquals(0, tmpFiles.size, "No temporary files should be left behind after atomic save")
    }

    @Test
    fun testConcurrentWritesOnSamePreferencesInstance() {
        val threads = mutableListOf<Thread>()
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val count = 20

        for (i in 0 until count) {
            val t = thread {
                try {
                    val pres = StickyWindowPresentation(
                        visible = (i % 2 == 0),
                        geometry = StickyWindowGeometry(
                            xDp = i * 10.0,
                            yDp = i * 10.0,
                            widthDp = 360.0 + i,
                            heightDp = 520.0 + i,
                            savedScaleX = 1.0,
                            savedScaleY = 1.0
                        )
                    )
                    preferences.save(pres)
                } catch (t: Throwable) {
                    errors.add(t)
                }
            }
            threads.add(t)
        }

        threads.forEach { it.join() }

        assertTrue(errors.isEmpty(), "Concurrent saves threw errors: $errors")
        assertTrue(prefsFile.exists())

        val loaded = preferences.load()
        assertTrue(loaded.geometry.widthDp >= 360.0)
        assertTrue(loaded.geometry.heightDp >= 520.0)
        assertEquals(1.0, loaded.geometry.savedScaleX)
        assertEquals(1.0, loaded.geometry.savedScaleY)
    }
}
