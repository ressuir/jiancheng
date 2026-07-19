package com.zookie.simpleschedule.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class EnumConverters {
    @TypeConverter fun taskSourceToString(value: TaskSource): String = value.name
    @TypeConverter fun stringToTaskSource(value: String): TaskSource = TaskSource.valueOf(value)
    @TypeConverter fun taskStatusToString(value: TaskStatus): String = value.name
    @TypeConverter fun stringToTaskStatus(value: String): TaskStatus = TaskStatus.valueOf(value)
    @TypeConverter fun changeSourceToString(value: ChangeSource): String = value.name
    @TypeConverter fun stringToChangeSource(value: String): ChangeSource = ChangeSource.valueOf(value)
}

@Database(
    entities = [
        ScheduleTaskEntity::class,
        TaskExecutionEntity::class,
        TaskRevisionEntity::class,
        ImportBatchEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(EnumConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        const val DATABASE_NAME = "jiancheng.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE import_batches ADD COLUMN sourceFileName TEXT")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

