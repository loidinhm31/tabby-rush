package dev.doggo.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StickyWindowGeometryTest {

    private val primaryMonitor = MonitorWorkArea(
        id = "primary",
        primary = true,
        xDp = 0.0,
        yDp = 0.0,
        widthDp = 1920.0,
        heightDp = 1080.0,
        scaleX = 1.0,
        scaleY = 1.0
    )

    private val leftSecondaryMonitor = MonitorWorkArea(
        id = "left-monitor",
        primary = false,
        xDp = -1920.0,
        yDp = 0.0,
        widthDp = 1920.0,
        heightDp = 1080.0,
        scaleX = 1.0,
        scaleY = 1.0
    )

    private val topSecondaryMonitor = MonitorWorkArea(
        id = "top-monitor",
        primary = false,
        xDp = 0.0,
        yDp = -1080.0,
        widthDp = 1920.0,
        heightDp = 1080.0,
        scaleX = 1.0,
        scaleY = 1.0
    )

    private val highDpiMonitor = MonitorWorkArea(
        id = "high-dpi",
        primary = true,
        xDp = 0.0,
        yDp = 0.0,
        widthDp = 1920.0,
        heightDp = 1080.0,
        scaleX = 2.0,
        scaleY = 2.0
    )

    @Test
    fun testEmptyMonitorListReturnsDefaultReachableGeometry() {
        val result = restoreStickyGeometry(saved = null, monitors = emptyList())
        assertEquals(0.0, result.xDp)
        assertEquals(0.0, result.yDp)
        assertEquals(DEFAULT_STICKY_WIDTH_DP, result.widthDp)
        assertEquals(DEFAULT_STICKY_HEIGHT_DP, result.heightDp)
        assertEquals(1.0, result.savedScaleX)
        assertEquals(1.0, result.savedScaleY)
    }

    @Test
    fun testNullSavedGeometryPlacesOnPrimaryMonitorWorkArea() {
        val monitors = listOf(primaryMonitor, leftSecondaryMonitor)
        val result = restoreStickyGeometry(saved = null, monitors = monitors)

        assertEquals(DEFAULT_STICKY_WIDTH_DP, result.widthDp)
        assertEquals(DEFAULT_STICKY_HEIGHT_DP, result.heightDp)
        assertEquals((1920.0 - 360.0) / 2.0, result.xDp)
        assertEquals((1080.0 - 520.0) / 2.0, result.yDp)
        assertEquals(1.0, result.savedScaleX)
        assertEquals(1.0, result.savedScaleY)
    }

    @Test
    fun testCorruptSavedGeometryFallsBackToDefaults() {
        val monitors = listOf(primaryMonitor)
        val corrupt = StickyWindowGeometry(
            xDp = Double.NaN,
            yDp = Double.POSITIVE_INFINITY,
            widthDp = -100.0,
            heightDp = 0.0,
            savedScaleX = -1.0,
            savedScaleY = 1.0
        )
        val result = restoreStickyGeometry(saved = corrupt, monitors = monitors)

        assertEquals(DEFAULT_STICKY_WIDTH_DP, result.widthDp)
        assertEquals(DEFAULT_STICKY_HEIGHT_DP, result.heightDp)
        assertTrue(result.xDp.isFinite())
        assertTrue(result.yDp.isFinite())
    }

    @Test
    fun testNegativeOriginsPreservedOnSecondaryMonitor() {
        val monitors = listOf(primaryMonitor, leftSecondaryMonitor)
        val saved = StickyWindowGeometry(
            xDp = -1500.0,
            yDp = 200.0,
            widthDp = 360.0,
            heightDp = 520.0,
            savedScaleX = 1.0,
            savedScaleY = 1.0
        )

        val result = restoreStickyGeometry(saved = saved, monitors = monitors)

        assertEquals(-1500.0, result.xDp)
        assertEquals(200.0, result.yDp)
        assertEquals(360.0, result.widthDp)
        assertEquals(520.0, result.heightDp)
    }

    @Test
    fun testNegativeOriginAbovePrimaryPreserved() {
        val monitors = listOf(primaryMonitor, topSecondaryMonitor)
        val saved = StickyWindowGeometry(
            xDp = 300.0,
            yDp = -800.0,
            widthDp = 360.0,
            heightDp = 520.0,
            savedScaleX = 1.0,
            savedScaleY = 1.0
        )

        val result = restoreStickyGeometry(saved = saved, monitors = monitors)

        assertEquals(300.0, result.xDp)
        assertEquals(-800.0, result.yDp)
        assertEquals(360.0, result.widthDp)
        assertEquals(520.0, result.heightDp)
    }

    @Test
    fun testHighDpiScalingMetadataRecorded() {
        val monitors = listOf(highDpiMonitor)
        val saved = StickyWindowGeometry(
            xDp = 100.0,
            yDp = 100.0,
            widthDp = 360.0,
            heightDp = 520.0,
            savedScaleX = 1.0,
            savedScaleY = 1.0
        )

        val result = restoreStickyGeometry(saved = saved, monitors = monitors)

        assertEquals(100.0, result.xDp)
        assertEquals(100.0, result.yDp)
        assertEquals(360.0, result.widthDp)
        assertEquals(520.0, result.heightDp)
        assertEquals(2.0, result.savedScaleX)
        assertEquals(2.0, result.savedScaleY)
    }

    @Test
    fun testHotplugDisconnectionFallbackToPrimary() {
        val monitors = listOf(primaryMonitor)
        val saved = StickyWindowGeometry(
            xDp = 2500.0,
            yDp = 300.0,
            widthDp = 360.0,
            heightDp = 520.0,
            savedScaleX = 1.0,
            savedScaleY = 1.0
        )

        val result = restoreStickyGeometry(saved = saved, monitors = monitors)

        assertEquals((1920.0 - 360.0) / 2.0, result.xDp)
        assertEquals((1080.0 - 520.0) / 2.0, result.yDp)
        assertEquals(360.0, result.widthDp)
        assertEquals(520.0, result.heightDp)
    }

    @Test
    fun testMinimumSizeEnforcement() {
        val monitors = listOf(primaryMonitor)
        val undersized = StickyWindowGeometry(
            xDp = 100.0,
            yDp = 100.0,
            widthDp = 150.0,
            heightDp = 180.0,
            savedScaleX = 1.0,
            savedScaleY = 1.0
        )

        val result = restoreStickyGeometry(saved = undersized, monitors = monitors)

        assertEquals(MIN_STICKY_WIDTH_DP, result.widthDp)
        assertEquals(MIN_STICKY_HEIGHT_DP, result.heightDp)
    }

    @Test
    fun testTinyMonitorDoesNotExpandBeyondWorkArea() {
        val tinyMonitor = MonitorWorkArea(
            id = "tiny",
            primary = true,
            xDp = 0.0,
            yDp = 0.0,
            widthDp = 200.0,
            heightDp = 200.0,
            scaleX = 1.0,
            scaleY = 1.0
        )

        val saved = StickyWindowGeometry(
            xDp = 0.0,
            yDp = 0.0,
            widthDp = 360.0,
            heightDp = 520.0,
            savedScaleX = 1.0,
            savedScaleY = 1.0
        )

        val result = restoreStickyGeometry(saved = saved, monitors = listOf(tinyMonitor))

        assertEquals(200.0, result.widthDp)
        assertEquals(200.0, result.heightDp)
        assertEquals(0.0, result.xDp)
        assertEquals(0.0, result.yDp)
    }

    @Test
    fun testTitleBarReachabilityClamping() {
        val monitorWithInsets = MonitorWorkArea(
            id = "insets-monitor",
            primary = true,
            xDp = 0.0,
            yDp = 40.0,
            widthDp = 1920.0,
            heightDp = 1000.0,
            scaleX = 1.0,
            scaleY = 1.0
        )

        val offScreenTop = StickyWindowGeometry(
            xDp = 100.0,
            yDp = -50.0,
            widthDp = 360.0,
            heightDp = 520.0,
            savedScaleX = 1.0,
            savedScaleY = 1.0
        )

        val topClamped = restoreStickyGeometry(saved = offScreenTop, monitors = listOf(monitorWithInsets))
        assertEquals(40.0, topClamped.yDp)

        val offScreenBottom = StickyWindowGeometry(
            xDp = 100.0,
            yDp = 900.0,
            widthDp = 360.0,
            heightDp = 520.0,
            savedScaleX = 1.0,
            savedScaleY = 1.0
        )

        val bottomClamped = restoreStickyGeometry(saved = offScreenBottom, monitors = listOf(monitorWithInsets))
        assertEquals(520.0, bottomClamped.yDp)
    }

    @Test
    fun testHorizontalReachabilityClamping() {
        val monitor = primaryMonitor

        // Off-screen to the right
        val offScreenRight = StickyWindowGeometry(
            xDp = 1800.0,
            yDp = 100.0,
            widthDp = 360.0,
            heightDp = 520.0,
            savedScaleX = 1.0,
            savedScaleY = 1.0
        )
        val rightClamped = restoreStickyGeometry(saved = offScreenRight, monitors = listOf(monitor))
        assertEquals(1920.0 - 360.0, rightClamped.xDp)

        // Off-screen to the left (negative x on a (0,0) monitor)
        val offScreenLeft = StickyWindowGeometry(
            xDp = -100.0,
            yDp = 100.0,
            widthDp = 360.0,
            heightDp = 520.0,
            savedScaleX = 1.0,
            savedScaleY = 1.0
        )
        val leftClamped = restoreStickyGeometry(saved = offScreenLeft, monitors = listOf(monitor))
        assertEquals(0.0, leftClamped.xDp)
    }

    @Test
    fun testMaximumIntersectionSelectionAcrossMultipleDisplays() {
        val left = MonitorWorkArea("left", false, -1920.0, 0.0, 1920.0, 1080.0, 1.0, 1.0)
        val right = MonitorWorkArea("right", true, 0.0, 0.0, 1920.0, 1080.0, 1.0, 1.0)

        val saved = StickyWindowGeometry(
            xDp = -100.0,
            yDp = 100.0,
            widthDp = 360.0,
            heightDp = 520.0,
            savedScaleX = 1.0,
            savedScaleY = 1.0
        )

        val result = restoreStickyGeometry(saved = saved, monitors = listOf(left, right))
        assertEquals(0.0, result.xDp)
        assertEquals(100.0, result.yDp)
    }

    @Test
    fun testFallbackToFirstMonitorWhenNoPrimaryDesignated() {
        val monitorA = MonitorWorkArea("a-non-primary", false, 100.0, 100.0, 1920.0, 1080.0, 1.0, 1.0)
        val result = restoreStickyGeometry(saved = null, monitors = listOf(monitorA))

        assertEquals(100.0 + (1920.0 - 360.0) / 2.0, result.xDp)
        assertEquals(100.0 + (1080.0 - 520.0) / 2.0, result.yDp)
    }

    @Test
    fun testTiedIntersectionAreaSelectsDeterministically() {
        val monitorA = MonitorWorkArea("monitor-a", false, -360.0, 0.0, 360.0, 600.0, 1.0, 1.0)
        val monitorB = MonitorWorkArea("monitor-b", false, 0.0, 0.0, 360.0, 600.0, 1.0, 1.0)

        val saved = StickyWindowGeometry(
            xDp = -180.0,
            yDp = 50.0,
            widthDp = 360.0,
            heightDp = 520.0,
            savedScaleX = 1.0,
            savedScaleY = 1.0
        )

        val result = restoreStickyGeometry(saved = saved, monitors = listOf(monitorA, monitorB))
        // Tied without primary: selects by monitor.id ascending ("monitor-a")
        // Clamped to monitorA: xDp clamped to -360.0
        assertEquals(-360.0, result.xDp)
        assertEquals(50.0, result.yDp)
    }

    @Test
    fun testTiedIntersectionWithOnePrimaryFavorsPrimary() {
        val nonPrimary = MonitorWorkArea("monitor-a", false, -360.0, 0.0, 360.0, 600.0, 1.0, 1.0)
        val primary = MonitorWorkArea("monitor-b", true, 0.0, 0.0, 360.0, 600.0, 1.0, 1.0)

        val saved = StickyWindowGeometry(
            xDp = -180.0,
            yDp = 50.0,
            widthDp = 360.0,
            heightDp = 520.0,
            savedScaleX = 1.0,
            savedScaleY = 1.0
        )

        val result = restoreStickyGeometry(saved = saved, monitors = listOf(nonPrimary, primary))
        // Tied with primary present: selects primary ("monitor-b")
        // Clamped to primary: xDp clamped to 0.0
        assertEquals(0.0, result.xDp)
        assertEquals(50.0, result.yDp)
    }
}
