package com.zookie.simpleschedule.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationInstrumentedTest {
    @Test fun migration1To2PreservesRowsAndAddsSafeNullableFileName() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-${UUID.randomUUID()}.db"
        val taskId = UUID.randomUUID().toString()
        val batchId = UUID.randomUUID().toString()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    createVersionOneSchema(db)
                    db.execSQL("INSERT INTO import_batches VALUES(?,1,'jiancheng.plan',1,'hash',1)", arrayOf(batchId))
                    db.execSQL(
                        "INSERT INTO schedule_tasks VALUES(?,'2026-07-20','迁移任务',NULL,540,600,NULL,'IMPORT','external',?,1,1)",
                        arrayOf(taskId, batchId),
                    )
                    db.execSQL("INSERT INTO task_executions VALUES(?,'PLANNED',1,NULL,NULL,NULL,NULL)", arrayOf(taskId))
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        helper.writableDatabase
        helper.close()

        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
        val migrated = kotlinx.coroutines.runBlocking { room.appDao().getTask(taskId) }
        val batch = kotlinx.coroutines.runBlocking { room.appDao().getImportBatches().single() }
        assertEquals("迁移任务", migrated?.task?.title)
        assertEquals(null, batch.sourceFileName)
        room.close()
        context.deleteDatabase(name)
    }

    private fun createVersionOneSchema(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS import_batches (id TEXT NOT NULL, schemaVersion INTEGER NOT NULL, format TEXT NOT NULL, importedAt INTEGER NOT NULL, contentHash TEXT NOT NULL, taskCount INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS schedule_tasks (id TEXT NOT NULL, date TEXT NOT NULL, title TEXT NOT NULL, details TEXT, plannedStartMinutes INTEGER NOT NULL, plannedEndMinutes INTEGER NOT NULL, category TEXT, source TEXT NOT NULL, externalId TEXT, importBatchId TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(importBatchId) REFERENCES import_batches(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_schedule_tasks_date_plannedStartMinutes ON schedule_tasks(date, plannedStartMinutes)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_schedule_tasks_externalId ON schedule_tasks(externalId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_schedule_tasks_importBatchId ON schedule_tasks(importBatchId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS task_executions (taskId TEXT NOT NULL, status TEXT NOT NULL, statusChangedAt INTEGER NOT NULL, completedAt INTEGER, annotation TEXT, annotationCreatedAt INTEGER, annotationUpdatedAt INTEGER, PRIMARY KEY(taskId), FOREIGN KEY(taskId) REFERENCES schedule_tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE IF NOT EXISTS task_revisions (id TEXT NOT NULL, taskId TEXT NOT NULL, revisionNumber INTEGER NOT NULL, previousTitle TEXT NOT NULL, previousDetails TEXT, previousDate TEXT NOT NULL, previousStartMinutes INTEGER NOT NULL, previousEndMinutes INTEGER NOT NULL, previousCategory TEXT, changedAt INTEGER NOT NULL, changeSource TEXT NOT NULL, PRIMARY KEY(id), FOREIGN KEY(taskId) REFERENCES schedule_tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_task_revisions_taskId ON task_revisions(taskId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_task_revisions_taskId_revisionNumber ON task_revisions(taskId, revisionNumber)")
    }
}
