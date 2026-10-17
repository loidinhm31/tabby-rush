package dev.tabbyrush.shared.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

/**
 * Creates a Room database builder for the Android platform backed by BundledSQLiteDriver.
 * Destructive migration fallback is strictly forbidden.
 */
fun createAndroidDatabaseBuilder(
    context: Context,
    dbFile: File
): RoomDatabase.Builder<TabbyRushDatabase> {
    val parent = dbFile.parentFile
    if (parent != null && !parent.exists()) {
        parent.mkdirs()
    }
    return Room.databaseBuilder<TabbyRushDatabase>(
        context = context.applicationContext,
        name = dbFile.absolutePath
    )
        .setDriver(BundledSQLiteDriver())
        .addMigrations(*DatabaseMigrations.ALL)
}
