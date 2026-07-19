package com.zookie.simpleschedule.data

import androidx.room.withTransaction
import com.zookie.simpleschedule.domain.TaskRules
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BackupFile(
    val format: String,
    val schemaVersion: Int,
    val exportedAt: Long,
    val timezone: String,
    val tasks: List<BackupTask>,
    val importBatches: List<BackupImportBatch>,
)

@Serializable
data class BackupTask(
    val id: String,
    val date: String,
    val title: String,
    val details: String? = null,
    val plannedStartMinutes: Int,
    val plannedEndMinutes: Int,
    val category: String? = null,
    val source: TaskSource,
    val externalId: String? = null,
    val importBatchId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val execution: BackupExecution,
    val revisions: List<BackupRevision>,
)

@Serializable
data class BackupExecution(
    val status: TaskStatus,
    val statusChangedAt: Long,
    val completedAt: Long? = null,
    val annotation: String? = null,
    val annotationCreatedAt: Long? = null,
    val annotationUpdatedAt: Long? = null,
)

@Serializable
data class BackupRevision(
    val id: String,
    val revisionNumber: Int,
    val previousTitle: String,
    val previousDetails: String? = null,
    val previousDate: String,
    val previousStartMinutes: Int,
    val previousEndMinutes: Int,
    val previousCategory: String? = null,
    val changedAt: Long,
    val changeSource: ChangeSource,
)

@Serializable
data class BackupImportBatch(
    val id: String,
    val schemaVersion: Int,
    val format: String,
    val importedAt: Long,
    val contentHash: String,
    val taskCount: Int,
    val sourceFileName: String? = null,
)

enum class BackupIssueCode {
    FILE_TOO_LARGE,
    INVALID_UTF8,
    NESTING_TOO_DEEP,
    JSON_STRUCTURE,
    WRONG_FORMAT,
    UNKNOWN_SCHEMA,
    INVALID_TIMEZONE,
    TASK_LIMIT,
    INVALID_TASK,
    DUPLICATE_ID,
    INVALID_EXECUTION,
    INVALID_ANNOTATION,
    INVALID_REVISION,
    INVALID_IMPORT_BATCH,
    MISSING_IMPORT_BATCH,
}

data class BackupIssue(
    val code: BackupIssueCode,
    val taskIndex: Int? = null,
    val detail: String? = null,
)

data class ValidatedBackup(val file: BackupFile)

sealed interface BackupDecodeResult {
    data class Success(val backup: ValidatedBackup) : BackupDecodeResult
    data class Failure(val issues: List<BackupIssue>) : BackupDecodeResult
}

data class BackupPreview(
    val backup: ValidatedBackup?,
    val issues: List<BackupIssue>,
    val taskCount: Int,
    val firstDate: LocalDate?,
    val lastDate: LocalDate?,
    val annotationCount: Int,
    val revisionCount: Int,
) {
    val canRestore: Boolean get() = backup != null && issues.isEmpty()
}

object BackupCodec {
    const val FORMAT = "jiancheng.backup"
    const val SCHEMA_VERSION = 1
    const val MAX_BYTES = 10 * 1024 * 1024
    const val MAX_TASKS = 10_000
    private const val MAX_NESTING = 32

    private val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(file)

    fun decode(bytes: ByteArray): BackupDecodeResult {
        if (bytes.size > MAX_BYTES) {
            return BackupDecodeResult.Failure(listOf(BackupIssue(BackupIssueCode.FILE_TOO_LARGE)))
        }
        val text = runCatching {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrNull() ?: return BackupDecodeResult.Failure(
            listOf(BackupIssue(BackupIssueCode.INVALID_UTF8)),
        )
        if (maxJsonNesting(text) > MAX_NESTING) {
            return BackupDecodeResult.Failure(
                listOf(BackupIssue(BackupIssueCode.NESTING_TOO_DEEP)),
            )
        }
        val file = try {
            json.decodeFromString<BackupFile>(text)
        } catch (_: SerializationException) {
            return BackupDecodeResult.Failure(listOf(BackupIssue(BackupIssueCode.JSON_STRUCTURE)))
        } catch (_: IllegalArgumentException) {
            return BackupDecodeResult.Failure(listOf(BackupIssue(BackupIssueCode.JSON_STRUCTURE)))
        }
        return validate(file)
    }

    internal fun validate(file: BackupFile): BackupDecodeResult {
        val issues = mutableListOf<BackupIssue>()
        if (file.format != FORMAT) issues += BackupIssue(BackupIssueCode.WRONG_FORMAT)
        if (file.schemaVersion != SCHEMA_VERSION) issues += BackupIssue(BackupIssueCode.UNKNOWN_SCHEMA)
        if (runCatching { ZoneId.of(file.timezone) }.isFailure) {
            issues += BackupIssue(BackupIssueCode.INVALID_TIMEZONE)
        }
        if (file.exportedAt < 0) issues += BackupIssue(BackupIssueCode.JSON_STRUCTURE)
        if (file.tasks.size > MAX_TASKS) issues += BackupIssue(BackupIssueCode.TASK_LIMIT)
        val batchIds = file.importBatches.map { it.id }.toSet()
        if (batchIds.size != file.importBatches.size) issues += BackupIssue(BackupIssueCode.DUPLICATE_ID)
        file.importBatches.forEach { batch ->
            val validBatch = isUuid(batch.id) && batch.schemaVersion == PlanCodec.SCHEMA_VERSION &&
                batch.format == PlanCodec.FORMAT && batch.importedAt >= 0 &&
                batch.contentHash.matches(Regex("[0-9a-fA-F]{64}")) &&
                batch.taskCount in 0..MAX_TASKS &&
                (batch.sourceFileName == null ||
                    (TaskRules.codePointLength(batch.sourceFileName) <= 128 &&
                        !containsUnsafeControlCharacter(batch.sourceFileName, allowLineBreaks = false)))
            if (!validBatch) issues += BackupIssue(BackupIssueCode.INVALID_IMPORT_BATCH)
        }
        val taskIds = mutableSetOf<String>()
        val revisionIds = mutableSetOf<String>()
        file.tasks.forEachIndexed { index, task ->
            if (!isUuid(task.id) || !taskIds.add(task.id)) {
                issues += BackupIssue(BackupIssueCode.DUPLICATE_ID, index, "task.id")
            }
            val validDate = TaskRules.parseDate(task.date) != null
            val validText = task.title.trim().isNotEmpty() && TaskRules.codePointLength(task.title.trim()) <= 120 &&
                (task.details == null || TaskRules.codePointLength(task.details) <= 1000) &&
                (task.category == null || TaskRules.codePointLength(task.category) <= 40) &&
                !containsUnsafeControlCharacter(task.title, allowLineBreaks = false) &&
                (task.details == null || !containsUnsafeControlCharacter(task.details, allowLineBreaks = true)) &&
                (task.category == null || !containsUnsafeControlCharacter(task.category, allowLineBreaks = false))
            val validSource = when (task.source) {
                TaskSource.MANUAL -> task.externalId == null && task.importBatchId == null
                TaskSource.IMPORT -> !task.externalId.isNullOrBlank() &&
                    TaskRules.codePointLength(task.externalId) <= 200 &&
                    !containsUnsafeControlCharacter(task.externalId, allowLineBreaks = false) &&
                    task.importBatchId in batchIds
            }
            if (!validDate || !validText || task.plannedStartMinutes !in 0..1439 ||
                task.plannedEndMinutes !in 1..1439 ||
                task.plannedEndMinutes <= task.plannedStartMinutes ||
                task.createdAt < 0 || task.updatedAt < 0 || !validSource
            ) {
                issues += BackupIssue(BackupIssueCode.INVALID_TASK, index)
            }
            if (task.source == TaskSource.IMPORT && task.importBatchId !in batchIds) {
                issues += BackupIssue(BackupIssueCode.MISSING_IMPORT_BATCH, index)
            }
            if (task.execution.status == TaskStatus.COMPLETED && task.execution.completedAt == null) {
                issues += BackupIssue(BackupIssueCode.INVALID_EXECUTION, index)
            }
            if (task.execution.status != TaskStatus.COMPLETED && task.execution.completedAt != null) {
                issues += BackupIssue(BackupIssueCode.INVALID_EXECUTION, index)
            }
            if (task.execution.statusChangedAt < 0 ||
                (task.execution.completedAt != null && task.execution.completedAt < 0)
            ) {
                issues += BackupIssue(BackupIssueCode.INVALID_EXECUTION, index)
            }
            if (task.execution.annotation != null &&
                TaskRules.validateAnnotation(task.execution.annotation) != null
            ) {
                issues += BackupIssue(BackupIssueCode.INVALID_ANNOTATION, index)
            }
            val annotationTimestampsValid = if (task.execution.annotation == null) {
                task.execution.annotationCreatedAt == null && task.execution.annotationUpdatedAt == null
            } else {
                task.execution.annotationCreatedAt?.let { it >= 0 } == true &&
                    task.execution.annotationUpdatedAt?.let { it >= 0 } == true
            }
            if (!annotationTimestampsValid) {
                issues += BackupIssue(BackupIssueCode.INVALID_ANNOTATION, index)
            }
            val revisionNumbers = mutableSetOf<Int>()
            task.revisions.forEach { revision ->
                if (!isUuid(revision.id) || !revisionIds.add(revision.id) ||
                    revision.revisionNumber <= 0 || !revisionNumbers.add(revision.revisionNumber) ||
                    TaskRules.parseDate(revision.previousDate) == null ||
                    revision.previousTitle.trim().isEmpty() ||
                    TaskRules.codePointLength(revision.previousTitle.trim()) > 120 ||
                    (revision.previousDetails != null &&
                        TaskRules.codePointLength(revision.previousDetails) > 1000) ||
                    (revision.previousCategory != null &&
                        TaskRules.codePointLength(revision.previousCategory) > 40) ||
                    containsUnsafeControlCharacter(revision.previousTitle, allowLineBreaks = false) ||
                    (revision.previousDetails != null &&
                        containsUnsafeControlCharacter(revision.previousDetails, allowLineBreaks = true)) ||
                    (revision.previousCategory != null &&
                        containsUnsafeControlCharacter(revision.previousCategory, allowLineBreaks = false)) ||
                    revision.previousStartMinutes !in 0..1439 ||
                    revision.previousEndMinutes !in 1..1439 ||
                    revision.previousEndMinutes <= revision.previousStartMinutes || revision.changedAt < 0
                ) {
                    issues += BackupIssue(BackupIssueCode.INVALID_REVISION, index)
                }
            }
            if (revisionNumbers.sorted() != (1..task.revisions.size).toList()) {
                issues += BackupIssue(BackupIssueCode.INVALID_REVISION, index)
            }
        }
        return if (issues.isEmpty()) BackupDecodeResult.Success(ValidatedBackup(file))
        else BackupDecodeResult.Failure(issues.distinct())
    }

    private fun isUuid(value: String): Boolean = runCatching {
        UUID.fromString(value).toString().equals(value, ignoreCase = true)
    }.getOrDefault(false)

    private fun containsUnsafeControlCharacter(value: String, allowLineBreaks: Boolean): Boolean =
        value.any { character ->
            character.code < 0x20 && (!allowLineBreaks || character !in listOf('\n', '\r', '\t'))
        }

    private fun maxJsonNesting(text: String): Int {
        var depth = 0
        var maximum = 0
        var inString = false
        var escaped = false
        text.forEach { character ->
            if (inString) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> inString = false
                }
            } else {
                when (character) {
                    '"' -> inString = true
                    '{', '[' -> {
                        depth++
                        maximum = maxOf(maximum, depth)
                    }
                    '}', ']' -> depth--
                }
            }
        }
        return maximum
    }
}

class BackupService(private val database: AppDatabase) {
    private val dao = database.appDao()

    suspend fun export(timeZone: ZoneId = ZoneId.systemDefault()): String {
        val tasks = dao.getAllTasks()
        val allRevisions = dao.getAllRevisions().groupBy { it.taskId }
        val batches = dao.getImportBatches()
        val file = BackupFile(
            format = BackupCodec.FORMAT,
            schemaVersion = BackupCodec.SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
            timezone = timeZone.id,
            tasks = tasks.map { item ->
                BackupTask(
                    id = item.task.id,
                    date = item.task.date,
                    title = item.task.title,
                    details = item.task.details,
                    plannedStartMinutes = item.task.plannedStartMinutes,
                    plannedEndMinutes = item.task.plannedEndMinutes,
                    category = item.task.category,
                    source = item.task.source,
                    externalId = item.task.externalId,
                    importBatchId = item.task.importBatchId,
                    createdAt = item.task.createdAt,
                    updatedAt = item.task.updatedAt,
                    execution = BackupExecution(
                        status = item.execution.status,
                        statusChangedAt = item.execution.statusChangedAt,
                        completedAt = item.execution.completedAt,
                        annotation = item.execution.annotation,
                        annotationCreatedAt = item.execution.annotationCreatedAt,
                        annotationUpdatedAt = item.execution.annotationUpdatedAt,
                    ),
                    revisions = allRevisions[item.task.id].orEmpty().map { revision ->
                        BackupRevision(
                            id = revision.id,
                            revisionNumber = revision.revisionNumber,
                            previousTitle = revision.previousTitle,
                            previousDetails = revision.previousDetails,
                            previousDate = revision.previousDate,
                            previousStartMinutes = revision.previousStartMinutes,
                            previousEndMinutes = revision.previousEndMinutes,
                            previousCategory = revision.previousCategory,
                            changedAt = revision.changedAt,
                            changeSource = revision.changeSource,
                        )
                    },
                )
            },
            importBatches = batches.map { batch ->
                BackupImportBatch(
                    id = batch.id,
                    schemaVersion = batch.schemaVersion,
                    format = batch.format,
                    importedAt = batch.importedAt,
                    contentHash = batch.contentHash,
                    taskCount = batch.taskCount,
                    sourceFileName = batch.sourceFileName,
                )
            },
        )
        return BackupCodec.encode(file)
    }

    fun preview(bytes: ByteArray): BackupPreview {
        val decoded = BackupCodec.decode(bytes)
        if (decoded is BackupDecodeResult.Failure) {
            return BackupPreview(null, decoded.issues, 0, null, null, 0, 0)
        }
        val backup = (decoded as BackupDecodeResult.Success).backup
        val dates = backup.file.tasks.mapNotNull { TaskRules.parseDate(it.date) }
        return BackupPreview(
            backup = backup,
            issues = emptyList(),
            taskCount = backup.file.tasks.size,
            firstDate = dates.minOrNull(),
            lastDate = dates.maxOrNull(),
            annotationCount = backup.file.tasks.count { !it.execution.annotation.isNullOrEmpty() },
            revisionCount = backup.file.tasks.sumOf { it.revisions.size },
        )
    }

    suspend fun restore(backup: ValidatedBackup) = restoreValidated(backup)

    internal suspend fun restoreValidated(backup: ValidatedBackup) {
        val file = backup.file
        database.withTransaction {
            dao.clearRevisions()
            dao.clearExecutions()
            dao.clearTasks()
            dao.clearBatches()
            dao.insertBatches(file.importBatches.map { batch ->
                ImportBatchEntity(
                    id = batch.id,
                    schemaVersion = batch.schemaVersion,
                    format = batch.format,
                    importedAt = batch.importedAt,
                    contentHash = batch.contentHash,
                    taskCount = batch.taskCount,
                    sourceFileName = batch.sourceFileName,
                )
            })
            file.tasks.forEach { task ->
                dao.insertTask(
                    ScheduleTaskEntity(
                        id = task.id,
                        date = task.date,
                        title = task.title,
                        details = task.details,
                        plannedStartMinutes = task.plannedStartMinutes,
                        plannedEndMinutes = task.plannedEndMinutes,
                        category = task.category,
                        source = task.source,
                        externalId = task.externalId,
                        importBatchId = task.importBatchId,
                        createdAt = task.createdAt,
                        updatedAt = task.updatedAt,
                    ),
                )
                dao.insertExecution(
                    TaskExecutionEntity(
                        taskId = task.id,
                        status = task.execution.status,
                        statusChangedAt = task.execution.statusChangedAt,
                        completedAt = task.execution.completedAt,
                        annotation = task.execution.annotation,
                        annotationCreatedAt = task.execution.annotationCreatedAt,
                        annotationUpdatedAt = task.execution.annotationUpdatedAt,
                    ),
                )
                dao.insertRevisions(task.revisions.map { revision ->
                    TaskRevisionEntity(
                        id = revision.id,
                        taskId = task.id,
                        revisionNumber = revision.revisionNumber,
                        previousTitle = revision.previousTitle,
                        previousDetails = revision.previousDetails,
                        previousDate = revision.previousDate,
                        previousStartMinutes = revision.previousStartMinutes,
                        previousEndMinutes = revision.previousEndMinutes,
                        previousCategory = revision.previousCategory,
                        changedAt = revision.changedAt,
                        changeSource = revision.changeSource,
                    )
                })
            }
        }
    }
}
