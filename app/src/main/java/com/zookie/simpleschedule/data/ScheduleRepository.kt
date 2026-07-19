package com.zookie.simpleschedule.data

import androidx.room.withTransaction
import com.zookie.simpleschedule.domain.TaskFieldError
import com.zookie.simpleschedule.domain.TaskInput
import com.zookie.simpleschedule.domain.TaskRules
import com.zookie.simpleschedule.domain.TaskValidationResult
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

sealed interface SaveTaskResult {
    data class Success(val taskId: String) : SaveTaskResult
    data class Invalid(val errors: List<TaskFieldError>) : SaveTaskResult
    data object OverlapConfirmationRequired : SaveTaskResult
    data object NotFound : SaveTaskResult
}

sealed interface AnnotationSaveResult {
    data object Success : AnnotationSaveResult
    data class Invalid(val error: TaskFieldError) : AnnotationSaveResult
    data object NotFound : AnnotationSaveResult
}

class ScheduleRepository(
    private val database: AppDatabase,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val dao: AppDao = database.appDao()

    fun observeDate(date: LocalDate): Flow<List<TaskWithExecution>> = dao.observeDate(date.toString())

    fun observeRange(start: LocalDate, end: LocalDate): Flow<List<TaskWithExecution>> =
        dao.observeRange(start.toString(), end.toString())

    suspend fun getRange(start: LocalDate, end: LocalDate): List<TaskWithExecution> =
        dao.getRange(start.toString(), end.toString())

    suspend fun getTask(id: String): TaskWithExecution? = dao.getTask(id)

    suspend fun getDetail(id: String): TaskDetail? {
        val task = dao.getTask(id) ?: return null
        return TaskDetail(task, dao.getRevisions(id))
    }

    suspend fun createTask(input: TaskInput, allowOverlap: Boolean = false): SaveTaskResult {
        val validated = TaskRules.validate(input)
        if (validated is TaskValidationResult.Invalid) return SaveTaskResult.Invalid(validated.errors)
        val value = (validated as TaskValidationResult.Valid).value
        if (!allowOverlap && hasOverlap(value.date, value.startMinutes, value.endMinutes, null)) {
            return SaveTaskResult.OverlapConfirmationRequired
        }
        val now = clock.millis()
        val id = UUID.randomUUID().toString()
        database.withTransaction {
            dao.insertTask(
                ScheduleTaskEntity(
                    id = id,
                    date = value.date.toString(),
                    title = value.title,
                    details = value.details,
                    plannedStartMinutes = value.startMinutes,
                    plannedEndMinutes = value.endMinutes,
                    category = value.category,
                    source = TaskSource.MANUAL,
                    externalId = null,
                    importBatchId = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            dao.insertExecution(newExecution(id, now))
        }
        return SaveTaskResult.Success(id)
    }

    suspend fun editTask(
        id: String,
        input: TaskInput,
        allowOverlap: Boolean = false,
    ): SaveTaskResult {
        val validated = TaskRules.validate(input)
        if (validated is TaskValidationResult.Invalid) return SaveTaskResult.Invalid(validated.errors)
        val value = (validated as TaskValidationResult.Valid).value
        val existing = dao.getTask(id)?.task ?: return SaveTaskResult.NotFound
        if (!allowOverlap && hasOverlap(value.date, value.startMinutes, value.endMinutes, id)) {
            return SaveTaskResult.OverlapConfirmationRequired
        }
        val changed = existing.title != value.title || existing.details != value.details ||
            existing.date != value.date.toString() ||
            existing.plannedStartMinutes != value.startMinutes ||
            existing.plannedEndMinutes != value.endMinutes || existing.category != value.category
        if (!changed) return SaveTaskResult.Success(id)
        val now = clock.millis()
        database.withTransaction {
            val revisionNumber = dao.getMaxRevisionNumber(id) + 1
            dao.insertRevision(TaskRules.snapshot(existing, revisionNumber, now))
            dao.updateTask(
                existing.copy(
                    title = value.title,
                    details = value.details,
                    date = value.date.toString(),
                    plannedStartMinutes = value.startMinutes,
                    plannedEndMinutes = value.endMinutes,
                    category = value.category,
                    updatedAt = now,
                ),
            )
        }
        return SaveTaskResult.Success(id)
    }

    suspend fun deleteTask(id: String): Boolean {
        val task = dao.getTask(id)?.task ?: return false
        dao.deleteTask(task)
        return true
    }

    suspend fun updateStatus(id: String, newStatus: TaskStatus): TaskExecutionEntity? {
        val existing = dao.getTask(id)?.execution ?: return null
        if (!TaskRules.canTransition(existing.status, newStatus)) return existing
        val now = clock.millis()
        dao.updateExecution(
            existing.copy(
                status = newStatus,
                statusChangedAt = now,
                completedAt = if (newStatus == TaskStatus.COMPLETED) now else null,
            ),
        )
        return existing
    }

    suspend fun restoreExecution(previous: TaskExecutionEntity) {
        if (dao.getTask(previous.taskId) != null) dao.updateExecution(previous)
    }

    suspend fun saveAnnotation(id: String, text: String): AnnotationSaveResult {
        TaskRules.validateAnnotation(text)?.let { return AnnotationSaveResult.Invalid(it) }
        val item = dao.getTask(id) ?: return AnnotationSaveResult.NotFound
        val annotation = text.trim().ifEmpty { null }
        val now = clock.millis()
        dao.updateExecution(
            item.execution.copy(
                annotation = annotation,
                annotationCreatedAt = when {
                    annotation == null -> null
                    item.execution.annotationCreatedAt != null -> item.execution.annotationCreatedAt
                    else -> now
                },
                annotationUpdatedAt = if (annotation == null) null else now,
            ),
        )
        return AnnotationSaveResult.Success
    }

    suspend fun clearAll() {
        database.withTransaction {
            dao.clearRevisions()
            dao.clearExecutions()
            dao.clearTasks()
            dao.clearBatches()
        }
    }

    suspend fun isEmpty(): Boolean = dao.taskCount() == 0

    private suspend fun hasOverlap(
        date: LocalDate,
        startMinutes: Int,
        endMinutes: Int,
        excludingId: String?,
    ): Boolean = dao.getRange(date.toString(), date.toString()).any { existing ->
        existing.task.id != excludingId && TaskRules.overlaps(
            startMinutes,
            endMinutes,
            existing.task.plannedStartMinutes,
            existing.task.plannedEndMinutes,
        )
    }

    companion object {
        fun newExecution(taskId: String, now: Long): TaskExecutionEntity = TaskExecutionEntity(
            taskId = taskId,
            status = TaskStatus.PLANNED,
            statusChangedAt = now,
            completedAt = null,
            annotation = null,
            annotationCreatedAt = null,
            annotationUpdatedAt = null,
        )
    }
}
