package dev.doggo.desktop

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID

/**
 * Atomic local persistence for sticky window presentation settings (geometry and visibility).
 * Stored outside Room/database in a dedicated properties file.
 *
 * Guarantees:
 * - Atomic write via temporary file in the same parent directory + fsync + ATOMIC_MOVE.
 * - Defensive recovery from missing, partial, or corrupted files falling back to default hidden presentation.
 * - Thread-safe synchronization on the instance level (single-writer within the process).
 * - Stores strictly presentation metadata; never contains account or task content.
 */
class StickyWindowPreferences(val file: File) {

    private val lock = Any()

    companion object {
        const val CURRENT_VERSION = 1
        const val KEY_VERSION = "version"
        const val KEY_VISIBLE = "visible"
        const val KEY_VISIBLE_ON_LAUNCH_ALIAS = "isVisibleOnLaunch"
        const val KEY_X_DP = "xDp"
        const val KEY_Y_DP = "yDp"
        const val KEY_WIDTH_DP = "widthDp"
        const val KEY_HEIGHT_DP = "heightDp"
        const val KEY_SCALE_X = "savedScaleX"
        const val KEY_SCALE_Y = "savedScaleY"

        val DEFAULT_PRESENTATION = StickyWindowPresentation(
            visible = false,
            geometry = StickyWindowGeometry(
                xDp = 0.0,
                yDp = 0.0,
                widthDp = DEFAULT_STICKY_WIDTH_DP,
                heightDp = DEFAULT_STICKY_HEIGHT_DP,
                savedScaleX = 1.0,
                savedScaleY = 1.0
            )
        )
    }

    /**
     * Loads the persisted presentation state. If the file is missing, empty, or contains
     * invalid/corrupted values, returns [DEFAULT_PRESENTATION] without throwing.
     */
    fun load(): StickyWindowPresentation = synchronized(lock) {
        if (!file.exists() || !file.isFile || file.length() == 0L) {
            return DEFAULT_PRESENTATION
        }

        return try {
            val properties = Properties()
            FileInputStream(file).use { fis ->
                properties.load(fis)
            }

            val version = properties.getProperty(KEY_VERSION)?.toIntOrNull() ?: CURRENT_VERSION
            if (version < 1) {
                return DEFAULT_PRESENTATION
            }

            val visibleStr = properties.getProperty(KEY_VISIBLE)
                ?: properties.getProperty(KEY_VISIBLE_ON_LAUNCH_ALIAS)
            val visible = visibleStr?.toBooleanStrictOrNull() ?: false

            val xDp = properties.getProperty(KEY_X_DP)?.toDoubleOrNull() ?: 0.0
            val yDp = properties.getProperty(KEY_Y_DP)?.toDoubleOrNull() ?: 0.0
            val widthDp = properties.getProperty(KEY_WIDTH_DP)?.toDoubleOrNull() ?: DEFAULT_STICKY_WIDTH_DP
            val heightDp = properties.getProperty(KEY_HEIGHT_DP)?.toDoubleOrNull() ?: DEFAULT_STICKY_HEIGHT_DP
            val scaleX = properties.getProperty(KEY_SCALE_X)?.toDoubleOrNull() ?: 1.0
            val scaleY = properties.getProperty(KEY_SCALE_Y)?.toDoubleOrNull() ?: 1.0

            val candidateGeometry = StickyWindowGeometry(
                xDp = xDp,
                yDp = yDp,
                widthDp = widthDp,
                heightDp = heightDp,
                savedScaleX = scaleX,
                savedScaleY = scaleY
            )

            if (!isValidStickyGeometry(candidateGeometry)) {
                DEFAULT_PRESENTATION.copy(visible = visible)
            } else {
                StickyWindowPresentation(
                    visible = visible,
                    geometry = candidateGeometry
                )
            }
        } catch (_: Exception) {
            DEFAULT_PRESENTATION
        }
    }

    /**
     * Atomically persists the presentation state to [file] using a UUID-named temporary file
     * in the parent directory, fsync, and atomic rename.
     *
     * In the event of an I/O error, the operation fails safely without corrupting the existing file.
     */
    fun save(presentation: StickyWindowPresentation): Unit = synchronized(lock) {
        val parentDir = file.canonicalFile.parentFile ?: file.absoluteFile.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }

        val tempFile = File(parentDir, "${file.name}.${UUID.randomUUID()}.tmp")
        try {
            val properties = Properties()
            properties.setProperty(KEY_VERSION, CURRENT_VERSION.toString())
            properties.setProperty(KEY_VISIBLE, presentation.visible.toString())
            properties.setProperty(KEY_X_DP, presentation.geometry.xDp.toString())
            properties.setProperty(KEY_Y_DP, presentation.geometry.yDp.toString())
            properties.setProperty(KEY_WIDTH_DP, presentation.geometry.widthDp.toString())
            properties.setProperty(KEY_HEIGHT_DP, presentation.geometry.heightDp.toString())
            properties.setProperty(KEY_SCALE_X, presentation.geometry.savedScaleX.toString())
            properties.setProperty(KEY_SCALE_Y, presentation.geometry.savedScaleY.toString())

            FileOutputStream(tempFile).use { fos ->
                properties.store(fos, "Doggo Sticky Window Presentation Preferences")
                fos.fd.sync()
            }

            try {
                Files.move(
                    tempFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    tempFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } catch (_: Exception) {
            // Best effort presentation persistence; prevent runtime crashes on disk/permission failure
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
}
