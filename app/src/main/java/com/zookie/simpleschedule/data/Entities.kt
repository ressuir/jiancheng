package com.zookie.simpleschedule.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

enum class TaskSource { MANUAL, IMPORT }

enum class TaskStatus { PLANNED, COMPLETED, SKIPPED, NOT_DONE, CANCELED }

enum class ChangeSource { USER, IMPORT, RESTORE }

@Entity(tableName = "import_batches")
data class ImportBatchEntity(
    @androidx.room.PrimaryKey val id: String,
    val schemaVersion: Int,
    val format: String,
    val importedAt: Long,
    val contentHash: String,
    val taskCount: Int,
    val sourceFileName: String? = null,
)

@Entity(
    tableName = "schedule_tasks",
    foreignKeys = [
        ForeignKey(
            entity = ImportBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["importBatchId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["date", "plannedStartMinutes"]),
        Index(value = ["externalId"], unique = true),
        Index(value = ["importBatchId"]),
    ],
)
data class ScheduleTaskEntity(
    @androidx.room.PrimaryKey val id: String,
    val date: String,
    val title: String,
    val details: String?,
    val plannedStartMinutes: Int,
    val plannedEndMinutes: Int,
    val category: String?,
    val source: TaskSource,
    val externalId: String?,
    val importBatchId: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "task_executions",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TaskExecutionEntity(
    @androidx.room.PrimaryKey val taskId: String,
    val status: TaskStatus,
    val statusChangedAt: Long,
    val completedAt: Long?,
    val annotation: String?,
    val annotationCreatedAt: Long?,
    val annotationUpdatedAt: Long?,
)

@Entity(
    tableName = "task_revisions",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["taskId"]),
        Index(value = ["taskId", "revisionNumber"], unique = true),
    ],
)
data class TaskRevisionEntity(
    @androidx.room.PrimaryKey val id: String,
    val taskId: String,
    val revisionNumber: Int,
    val previousTitle: String,
    val previousDetails: String?,
    val previousDate: String,
    val previousStartMinutes: Int,
    val previousEndMinutes: Int,
    val previousCategory: String?,
    val changedAt: Long,
    val changeSource: ChangeSource,
)

data class TaskWithExecution(
    @androidx.room.Embedded val task: ScheduleTaskEntity,
    @androidx.room.Embedded val execution: TaskExecutionEntity,
)

data class TaskDetail(
    val item: TaskWithExecution,
    val revisions: List<TaskRevisionEntity>,
)

