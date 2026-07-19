package com.zookie.simpleschedule.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query(
        """
        SELECT schedule_tasks.*, task_executions.*
        FROM schedule_tasks
        INNER JOIN task_executions ON task_executions.taskId = schedule_tasks.id
        WHERE schedule_tasks.date = :date
        ORDER BY schedule_tasks.plannedStartMinutes, schedule_tasks.plannedEndMinutes, schedule_tasks.title
        """,
    )
    fun observeDate(date: String): Flow<List<TaskWithExecution>>

    @Query(
        """
        SELECT schedule_tasks.*, task_executions.*
        FROM schedule_tasks
        INNER JOIN task_executions ON task_executions.taskId = schedule_tasks.id
        WHERE schedule_tasks.date BETWEEN :startDate AND :endDate
        ORDER BY schedule_tasks.date, schedule_tasks.plannedStartMinutes, schedule_tasks.title
        """,
    )
    fun observeRange(startDate: String, endDate: String): Flow<List<TaskWithExecution>>

    @Query(
        """
        SELECT schedule_tasks.*, task_executions.*
        FROM schedule_tasks
        INNER JOIN task_executions ON task_executions.taskId = schedule_tasks.id
        WHERE schedule_tasks.date BETWEEN :startDate AND :endDate
        ORDER BY schedule_tasks.date, schedule_tasks.plannedStartMinutes, schedule_tasks.title
        """,
    )
    suspend fun getRange(startDate: String, endDate: String): List<TaskWithExecution>

    @Query(
        """
        SELECT schedule_tasks.*, task_executions.*
        FROM schedule_tasks
        INNER JOIN task_executions ON task_executions.taskId = schedule_tasks.id
        ORDER BY schedule_tasks.date, schedule_tasks.plannedStartMinutes, schedule_tasks.title
        """,
    )
    suspend fun getAllTasks(): List<TaskWithExecution>

    @Query(
        """
        SELECT schedule_tasks.*, task_executions.*
        FROM schedule_tasks
        INNER JOIN task_executions ON task_executions.taskId = schedule_tasks.id
        WHERE schedule_tasks.id = :id
        """,
    )
    suspend fun getTask(id: String): TaskWithExecution?

    @Query("SELECT * FROM schedule_tasks WHERE externalId IS NOT NULL")
    suspend fun getImportedTasks(): List<ScheduleTaskEntity>

    @Query("SELECT * FROM task_revisions WHERE taskId = :taskId ORDER BY revisionNumber")
    suspend fun getRevisions(taskId: String): List<TaskRevisionEntity>

    @Query("SELECT * FROM task_revisions ORDER BY taskId, revisionNumber")
    suspend fun getAllRevisions(): List<TaskRevisionEntity>

    @Query("SELECT COALESCE(MAX(revisionNumber), 0) FROM task_revisions WHERE taskId = :taskId")
    suspend fun getMaxRevisionNumber(taskId: String): Int

    @Query("SELECT * FROM import_batches ORDER BY importedAt")
    suspend fun getImportBatches(): List<ImportBatchEntity>

    @Query("SELECT COUNT(*) FROM schedule_tasks")
    suspend fun taskCount(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTask(task: ScheduleTaskEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTasks(tasks: List<ScheduleTaskEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExecution(execution: TaskExecutionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExecutions(executions: List<TaskExecutionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRevision(revision: TaskRevisionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRevisions(revisions: List<TaskRevisionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBatch(batch: ImportBatchEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBatches(batches: List<ImportBatchEntity>)

    @Update
    suspend fun updateTask(task: ScheduleTaskEntity)

    @Update
    suspend fun updateExecution(execution: TaskExecutionEntity)

    @Delete
    suspend fun deleteTask(task: ScheduleTaskEntity)

    @Query("DELETE FROM task_revisions")
    suspend fun clearRevisions()

    @Query("DELETE FROM task_executions")
    suspend fun clearExecutions()

    @Query("DELETE FROM schedule_tasks")
    suspend fun clearTasks()

    @Query("DELETE FROM import_batches")
    suspend fun clearBatches()
}

