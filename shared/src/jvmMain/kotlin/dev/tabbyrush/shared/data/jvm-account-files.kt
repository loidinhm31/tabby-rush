package dev.tabbyrush.shared.data

import dev.tabbyrush.shared.contract.AccountKey
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * JvmAccountFiles manages account directory partitioning, database files,
 * persistent generation counters, and fail-closed wipe markers for JVM targets.
 */
class JvmAccountFiles(
    val baseDir: File = defaultBaseDir()
) {
    init {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
    }

    fun getAccountDirectory(accountKey: AccountKey): File {
        val dir = File(baseDir, accountKey.value)
        val canonicalBase = baseDir.canonicalFile
        val canonicalDir = dir.canonicalFile
        if (!canonicalDir.toPath().startsWith(canonicalBase.toPath())) {
            throw SecurityException("Path traversal attempt detected for accountKey: ${accountKey.value}")
        }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getDatabaseFile(accountKey: AccountKey): File {
        return File(getAccountDirectory(accountKey), "tabbyrush.db")
    }

    fun getWipeMarkerFile(accountKey: AccountKey): File {
        return File(getAccountDirectory(accountKey), "wipe.marker")
    }

    fun getGenerationFile(accountKey: AccountKey): File {
        return File(getAccountDirectory(accountKey), "generation.dat")
    }

    fun hasPendingWipe(accountKey: AccountKey): Boolean {
        return getWipeMarkerFile(accountKey).exists()
    }

    fun writeWipeMarker(accountKey: AccountKey, nextGeneration: Long) {
        val markerFile = getWipeMarkerFile(accountKey)
        val tempFile = File(markerFile.parentFile, "wipe.marker.tmp")
        FileOutputStream(tempFile).use { fos ->
            fos.write("generation=$nextGeneration\ntimestamp=${System.currentTimeMillis()}\n".toByteArray(Charsets.UTF_8))
            fos.fd.sync()
        }
        Files.move(
            tempFile.toPath(),
            markerFile.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
    }

    fun clearWipeMarker(accountKey: AccountKey) {
        val markerFile = getWipeMarkerFile(accountKey)
        if (markerFile.exists()) {
            markerFile.delete()
        }
    }

    fun readGeneration(accountKey: AccountKey): Long {
        val genFile = getGenerationFile(accountKey)
        if (!genFile.exists()) return 0L
        return try {
            genFile.readText(Charsets.UTF_8).trim().toLong()
        } catch (_: Exception) {
            0L
        }
    }

    fun writeGeneration(accountKey: AccountKey, generation: Long) {
        val genFile = getGenerationFile(accountKey)
        val tempFile = File(genFile.parentFile, "generation.dat.tmp")
        FileOutputStream(tempFile).use { fos ->
            fos.write(generation.toString().toByteArray(Charsets.UTF_8))
            fos.fd.sync()
        }
        Files.move(
            tempFile.toPath(),
            genFile.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
    }

    fun deleteDatabaseAndArtifacts(accountKey: AccountKey): Boolean {
        val dir = getAccountDirectory(accountKey)
        val dbFile = File(dir, "tabbyrush.db")
        val walFile = File(dir, "tabbyrush.db-wal")
        val shmFile = File(dir, "tabbyrush.db-shm")
        var allDeleted = true
        if (dbFile.exists() && !dbFile.delete()) allDeleted = false
        if (walFile.exists() && !walFile.delete()) allDeleted = false
        if (shmFile.exists() && !shmFile.delete()) allDeleted = false
        return allDeleted
    }

    companion object {
        fun defaultBaseDir(): File {
            val os = System.getProperty("os.name")?.lowercase() ?: ""
            return when {
                os.contains("win") -> {
                    val appData = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
                    File(appData, "tabbyrush/accounts")
                }
                os.contains("mac") -> {
                    val home = System.getProperty("user.home")
                    File(home, "Library/Application Support/tabbyrush/accounts")
                }
                else -> {
                    val xdgData = System.getenv("XDG_DATA_HOME")
                    if (!xdgData.isNullOrBlank()) {
                        File(xdgData, "tabbyrush/accounts")
                    } else {
                        File(System.getProperty("user.home"), ".local/share/tabbyrush/accounts")
                    }
                }
            }
        }
    }
}
