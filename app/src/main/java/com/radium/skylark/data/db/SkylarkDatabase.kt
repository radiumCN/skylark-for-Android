package com.radium.skylark.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ProfileEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SkylarkDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    companion object {
        const val NAME = "skylark.db"
    }
}
