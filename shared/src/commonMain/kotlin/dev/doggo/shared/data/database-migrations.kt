package dev.doggo.shared.data

import androidx.room.migration.Migration

/**
 * Explicit migrations catalog for DoggoDatabase.
 * Destructive fallback is strictly forbidden. Schema changes must be accompanied
 * by verified step-by-step migrations.
 */
object DatabaseMigrations {
    /**
     * Initial schema is version 1. No migrations yet exist.
     * Downward migrations or unhandled versions will fail fast.
     */
    val ALL: Array<Migration> = emptyArray()
}
