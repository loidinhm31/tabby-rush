package dev.tabbyrush.desktop

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException

/**
 * SingleInstanceGuard ensures only one TabbyRush desktop process runs per user profile/directory.
 *
 * Invariants:
 * 1. Must be acquired BEFORE vault or database initialization.
 * 2. Uses OS file channel locks (fcntl/flock on Linux) to prevent concurrent token refresh and DB races.
 * 3. Keeps the lock file in place across close/exit to avoid inode recycling and race conditions.
 */
class SingleInstanceGuard private constructor(
    private val lockFile: File,
    private val randomAccessFile: RandomAccessFile,
    private val channel: FileChannel,
    private val lock: FileLock
) : AutoCloseable {

    override fun close() {
        try {
            if (lock.isValid) {
                lock.release()
            }
        } catch (_: Exception) {
            // Ignore release exceptions during shutdown
        } finally {
            try {
                channel.close()
            } catch (_: Exception) {}
            try {
                randomAccessFile.close()
            } catch (_: Exception) {}
            // Invariant: Do NOT delete lockFile to prevent inode races between separate processes
        }
    }

    companion object {
        /**
         * Attempts to acquire an exclusive lock on [lockFile].
         * Returns an active [SingleInstanceGuard] if successful, or `null` if another instance is running.
         */
        fun tryAcquire(lockFile: File): SingleInstanceGuard? {
            val parent = lockFile.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }

            return try {
                val raf = RandomAccessFile(lockFile, "rw")
                val channel = raf.channel
                val lock = try {
                    channel.tryLock()
                } catch (_: OverlappingFileLockException) {
                    null
                }

                if (lock == null) {
                    try { channel.close() } catch (_: Exception) {}
                    try { raf.close() } catch (_: Exception) {}
                    null
                } else {
                    // Record process identity for diagnostics
                    try {
                        val pid = ProcessHandle.current().pid()
                        raf.setLength(0)
                        raf.writeBytes("pid=$pid\ntimestamp=${System.currentTimeMillis()}\n")
                    } catch (_: Exception) {
                        // Non-critical diagnostic write
                    }
                    SingleInstanceGuard(lockFile, raf, channel, lock)
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
