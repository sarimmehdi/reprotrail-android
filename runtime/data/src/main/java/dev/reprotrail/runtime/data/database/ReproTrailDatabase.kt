package dev.reprotrail.runtime.data.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import dev.reprotrail.runtime.data.dao.TraceActionDao
import dev.reprotrail.runtime.data.dao.TraceSessionDao
import dev.reprotrail.runtime.data.dao.TraceUploadDao
import dev.reprotrail.runtime.data.entity.TraceActionEntity
import dev.reprotrail.runtime.data.entity.TraceSessionEntity

/** Room database containing bounded local ReproTrail sessions and actions. */
@Database(
    entities = [TraceSessionEntity::class, TraceActionEntity::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class ReproTrailDatabase : RoomDatabase() {
    /** Returns the TraceSessionDao managed by this database. */
    abstract fun traceSessionDao(): TraceSessionDao

    /** Returns the TraceActionDao managed by this database. */
    abstract fun traceActionDao(): TraceActionDao

    /** Returns the TraceUploadDao managed by this database. */
    abstract fun traceUploadDao(): TraceUploadDao

    /** Constants used to configure this Room database. */
    companion object {
        /** File name used to create this Room database. */
        const val DATABASE_NAME = "reprotrail.db"
    }
}
