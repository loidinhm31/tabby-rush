package dev.tabbyrush.desktop

/**
 * Representation of sticky window logical geometry in device-independent pixels (dp)
 * along with display scale conversion metadata.
 */
data class StickyWindowGeometry(
    val xDp: Double,
    val yDp: Double,
    val widthDp: Double,
    val heightDp: Double,
    val savedScaleX: Double,
    val savedScaleY: Double
)

/**
 * Work area of a connected display monitor in normalized AWT user-space logical units (dp).
 * The work area accounts for system insets (taskbars, docks, menu bars).
 */
data class MonitorWorkArea(
    val id: String,
    val primary: Boolean,
    val xDp: Double,
    val yDp: Double,
    val widthDp: Double,
    val heightDp: Double,
    val scaleX: Double,
    val scaleY: Double
)

/**
 * Presentation state persisted for the sticky window across sessions.
 */
data class StickyWindowPresentation(
    val visible: Boolean,
    val geometry: StickyWindowGeometry
)

const val DEFAULT_STICKY_WIDTH_DP = 360.0
const val DEFAULT_STICKY_HEIGHT_DP = 520.0
const val MIN_STICKY_WIDTH_DP = 280.0
const val MIN_STICKY_HEIGHT_DP = 240.0
const val MIN_CONTENT_VISIBLE_DP = 48.0
const val MIN_TITLE_BAR_VISIBLE_DP = 32.0

/**
 * Validates whether the given geometry values are finite and non-empty.
 */
fun isValidStickyGeometry(geometry: StickyWindowGeometry): Boolean {
    return geometry.xDp.isFinite() &&
        geometry.yDp.isFinite() &&
        geometry.widthDp.isFinite() && geometry.widthDp > 0.0 &&
        geometry.heightDp.isFinite() && geometry.heightDp > 0.0 &&
        geometry.savedScaleX.isFinite() && geometry.savedScaleX > 0.0 &&
        geometry.savedScaleY.isFinite() && geometry.savedScaleY > 0.0
}

/**
 * Validates whether the monitor work area values are finite and non-empty.
 */
fun isValidMonitorWorkArea(monitor: MonitorWorkArea): Boolean {
    return monitor.xDp.isFinite() &&
        monitor.yDp.isFinite() &&
        monitor.widthDp.isFinite() && monitor.widthDp > 0.0 &&
        monitor.heightDp.isFinite() && monitor.heightDp > 0.0 &&
        monitor.scaleX.isFinite() && monitor.scaleX > 0.0 &&
        monitor.scaleY.isFinite() && monitor.scaleY > 0.0
}

/**
 * Restores and clamps sticky window geometry against the current active monitor work areas.
 *
 * Rules:
 * 1. If [monitors] is empty or contains no valid monitor, returns reachable default geometry (0,0, 360x520, scale 1.0).
 * 2. If [saved] is null or corrupt (NaN, infinite, non-positive dimensions/scale), falls back to default 360x520
 *    centered on the primary monitor (or first available).
 * 3. Identifies target monitor by maximum intersection area. If the saved window has zero intersection with all
 *    currently connected monitors (e.g. secondary monitor was unplugged), falls back to the primary (or first) monitor.
 * 4. Preserves legitimate negative origins (e.g. secondary display positioned to the left of or above the primary).
 * 5. Clamps dimensions to at least [MIN_STICKY_WIDTH_DP] x [MIN_STICKY_HEIGHT_DP], unless the monitor work area itself
 *    is smaller than minimums (in which case dimensions clamp to the work area).
 * 6. Clamps position so the entire title bar is reachable (yDp >= monitor.yDp and within horizontal bounds)
 *    and content is visible within the usable work area.
 * 7. Records the target monitor's current display scale in the returned geometry.
 */
fun restoreStickyGeometry(
    saved: StickyWindowGeometry?,
    monitors: List<MonitorWorkArea>
): StickyWindowGeometry {
    val validMonitors = monitors.filter { isValidMonitorWorkArea(it) }
    if (validMonitors.isEmpty()) {
        val safeSaved = saved?.takeIf { isValidStickyGeometry(it) }
        return safeSaved ?: StickyWindowGeometry(
            xDp = 0.0,
            yDp = 0.0,
            widthDp = DEFAULT_STICKY_WIDTH_DP,
            heightDp = DEFAULT_STICKY_HEIGHT_DP,
            savedScaleX = 1.0,
            savedScaleY = 1.0
        )
    }

    val primaryMonitor = validMonitors.firstOrNull { it.primary } ?: validMonitors.first()

    if (saved == null || !isValidStickyGeometry(saved)) {
        val clampedW = minOf(DEFAULT_STICKY_WIDTH_DP, primaryMonitor.widthDp)
        val clampedH = minOf(DEFAULT_STICKY_HEIGHT_DP, primaryMonitor.heightDp)
        val clampedX = primaryMonitor.xDp + maxOf(0.0, (primaryMonitor.widthDp - clampedW) / 2.0)
        val clampedY = primaryMonitor.yDp + maxOf(0.0, (primaryMonitor.heightDp - clampedH) / 2.0)
        return StickyWindowGeometry(
            xDp = clampedX,
            yDp = clampedY,
            widthDp = clampedW,
            heightDp = clampedH,
            savedScaleX = primaryMonitor.scaleX,
            savedScaleY = primaryMonitor.scaleY
        )
    }

    // Determine target monitor based on maximum intersection area with saved bounds
    var maxIntersectionArea = 0.0
    var candidateMonitors = mutableListOf<MonitorWorkArea>()

    for (monitor in validMonitors) {
        val overlapLeft = maxOf(saved.xDp, monitor.xDp)
        val overlapTop = maxOf(saved.yDp, monitor.yDp)
        val overlapRight = minOf(saved.xDp + saved.widthDp, monitor.xDp + monitor.widthDp)
        val overlapBottom = minOf(saved.yDp + saved.heightDp, monitor.yDp + monitor.heightDp)
        val overlapWidth = maxOf(0.0, overlapRight - overlapLeft)
        val overlapHeight = maxOf(0.0, overlapBottom - overlapTop)
        val area = overlapWidth * overlapHeight

        if (area > maxIntersectionArea) {
            maxIntersectionArea = area
            candidateMonitors = mutableListOf(monitor)
        } else if (area > 0.0 && area == maxIntersectionArea) {
            candidateMonitors.add(monitor)
        }
    }

    val (targetMonitor, hasIntersection) = if (maxIntersectionArea > 0.0) {
        val selected = candidateMonitors.firstOrNull { it.primary }
            ?: candidateMonitors.minByOrNull { it.id }
            ?: candidateMonitors.first()
        Pair(selected, true)
    } else {
        // Disconnected monitor fallback
        Pair(primaryMonitor, false)
    }

    // Clamp dimensions to target monitor work area and minimum constraints
    val effectiveMinWidth = minOf(MIN_STICKY_WIDTH_DP, targetMonitor.widthDp)
    val effectiveMinHeight = minOf(MIN_STICKY_HEIGHT_DP, targetMonitor.heightDp)
    val effectiveMaxWidth = targetMonitor.widthDp
    val effectiveMaxHeight = targetMonitor.heightDp

    val clampedWidth = saved.widthDp.coerceIn(
        effectiveMinWidth,
        maxOf(effectiveMinWidth, effectiveMaxWidth)
    )
    val clampedHeight = saved.heightDp.coerceIn(
        effectiveMinHeight,
        maxOf(effectiveMinHeight, effectiveMaxHeight)
    )

    // Clamp position ensuring title bar reachability and content visibility
    val clampedX: Double
    val clampedY: Double

    if (!hasIntersection) {
        // Fallback placement: center within the target monitor work area
        clampedX = targetMonitor.xDp + maxOf(0.0, (targetMonitor.widthDp - clampedWidth) / 2.0)
        clampedY = targetMonitor.yDp + maxOf(0.0, (targetMonitor.heightDp - clampedHeight) / 2.0)
    } else {
        // Horizontal clamp: entire title bar must be within work area bounds
        val maxX = maxOf(targetMonitor.xDp, targetMonitor.xDp + targetMonitor.widthDp - clampedWidth)
        clampedX = saved.xDp.coerceIn(targetMonitor.xDp, maxX)

        // Vertical clamp: top edge (title bar) must be at least monitor.yDp (reachable),
        // and window must not extend below the usable work area
        val maxY = maxOf(targetMonitor.yDp, targetMonitor.yDp + targetMonitor.heightDp - clampedHeight)
        clampedY = saved.yDp.coerceIn(targetMonitor.yDp, maxY)
    }

    return StickyWindowGeometry(
        xDp = clampedX,
        yDp = clampedY,
        widthDp = clampedWidth,
        heightDp = clampedHeight,
        savedScaleX = targetMonitor.scaleX,
        savedScaleY = targetMonitor.scaleY
    )
}
