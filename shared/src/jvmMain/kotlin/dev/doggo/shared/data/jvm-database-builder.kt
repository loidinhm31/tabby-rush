package dev.doggo.shared.data

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

/**
 * Creates a Room database builder for the JVM platform backed by BundledSQLiteDriver.
 * Explicitly forbids destructive migration fallback and registers migrations.
 */
fun createJvmDatabaseBuilder(dbFile: File): RoomDatabase.Builder<DoggoDatabase> {
    val parent = dbFile.parentFile
    if (parent != null && !parent.exists()) {
        parent.mkdirs()
    }
    return Room.databaseBuilder<DoggoDatabase>(
        name = dbFile.absolutePath
    )
        .setDriver(BundledSQLiteDriver())
        .addMigrations(*DatabaseMigrations.ALL)
}

/**
 * Creates an in-memory database builder for JVM isolated testing.
 */
fun createInMemoryJvmDatabaseBuilder(): RoomDatabase.Builder<DoggoDatabase> {
    return Room.inMemoryDatabaseBuilder<DoggoDatabase>()
        .setDriver(BundledSQLiteDriver())
}
