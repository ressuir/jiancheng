package com.zookie.simpleschedule.data

import androidx.room.withTransaction
import com.zookie.simpleschedule.domain.TaskRules
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
data class PlanFile(
    val format: String,
    val schemaVersion: Int,
    val timezone: String,
    val days: List<PlanDay>,
)

@Serializable
data class PlanDay(
    val date: String,
    val tasks: List<PlanTask>,
)

@Serializable
data class PlanTask(
    val id: String,
    val title: String,
    val details: String? = null,
    val start: String,
    val end: String,
    val category: String? = null,
)

data class ValidatedPlanTask(
    val externalId: String,
    val date: LocalDate,
    val title: String,
    val details: String?,
    val startMinutes: Int,
    val endMinutes: Int,
    val category: String?,
)

data class ValidatedPlan(
    val timezone: String,
    val tasks: List<ValidatedPlanTask>,
    val contentHash: String,
) {
    val firstDate: LocalDate? get() = tasks.minOfOrNull { it.date }
    val lastDate: LocalDate? get() = tasks.maxOfOrNull { it.date }
}

enum class PlanIssueCode {
    FILE_TOO_LARGE,
    INVALID_UTF8,
    NESTING_TOO_DEEP,
    JSON_STRUCTURE,
    WRONG_FORMAT,
    UNKNOWN_SCHEMA,
    INVALID_TIMEZONE,
    INVALID_DATE,
    ID_REQUIRED,
    ID_TOO_LONG,
    DUPLICATE_ID,
    TITLE_REQUIRED,
    TITLE_TOO_LONG,
    DETAILS_TOO_LONG,
    CATEGORY_TOO_LONG,
    INVALID_START,
    INVALID_END,
    END_NOT_AFTER_START,
    TASK_LIMIT,
    DATE_SPAN_LIMIT,
    INVALID_TEXT,
    EXISTING_ID_CONFLICT,
}

data class PlanIssue(
    val code: PlanIssueCode,
    val dayIndex: Int? = null,
    val taskIndex: Int? = null,
    val field: String? = null,
    val detail: String? = null,
)

sealed interface PlanDecodeResult {
    data class Success(val plan: ValidatedPlan) : PlanDecodeResult
    data class Failure(val issues: List<PlanIssue>) : PlanDecodeResult
}

object PlanCodec {
    const val FORMAT = "jiancheng.plan"
    const val SCHEMA_VERSION = 1
    const val MAX_BYTES = 1024 * 1024
    const val MAX_TASKS = 2000
    private const val MAX_NESTING = 24
    private const val MAX_ID_LENGTH = 200

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        explicitNulls = false
        encodeDefaults = true
    }

    fun decode(bytes: ByteArray): PlanDecodeResult {
        if (bytes.size > MAX_BYTES) {
            return PlanDecodeResult.Failure(listOf(PlanIssue(PlanIssueCode.FILE_TOO_LARGE)))
        }
        val text = decodeUtf8(bytes) ?: return PlanDecodeResult.Failure(
            listOf(PlanIssue(PlanIssueCode.INVALID_UTF8)),
        )
        if (maxJsonNesting(text) > MAX_NESTING) {
            return PlanDecodeResult.Failure(listOf(PlanIssue(PlanIssueCode.NESTING_TOO_DEEP)))
        }
        val file = try {
            json.decodeFromString<PlanFile>(text)
        } catch (_: SerializationException) {
            return PlanDecodeResult.Failure(listOf(PlanIssue(PlanIssueCode.JSON_STRUCTURE)))
        } catch (_: IllegalArgumentException) {
            return PlanDecodeResult.Failure(listOf(PlanIssue(PlanIssueCode.JSON_STRUCTURE)))
        }
        return validate(file, sha256(bytes))
    }

    internal fun validate(file: PlanFile, contentHash: String = "test"): PlanDecodeResult {
        val issues = mutableListOf<PlanIssue>()
        if (file.format != FORMAT) issues += PlanIssue(PlanIssueCode.WRONG_FORMAT, field = "format")
        if (file.schemaVersion != SCHEMA_VERSION) {
            issues += PlanIssue(PlanIssueCode.UNKNOWN_SCHEMA, field = "schemaVersion")
        }
        if (runCatching { ZoneId.of(file.timezone) }.isFailure) {
            issues += PlanIssue(PlanIssueCode.INVALID_TIMEZONE, field = "timezone")
        }

        val totalTasks = file.days.sumOf { it.tasks.size }
        if (totalTasks > MAX_TASKS) issues += PlanIssue(PlanIssueCode.TASK_LIMIT)
        val seenIds = mutableSetOf<String>()
        val validated = mutableListOf<ValidatedPlanTask>()

        file.days.forEachIndexed { dayIndex, day ->
            val date = TaskRules.parseDate(day.date)
            if (date == null) {
                issues += PlanIssue(PlanIssueCode.INVALID_DATE, dayIndex, field = "date")
            }
            day.tasks.forEachIndexed { taskIndex, task ->
                val id = task.id.trim()
                val title = task.title.trim()
                val details = task.details?.trim()?.ifEmpty { null }
                val category = task.category?.trim()?.ifEmpty { null }
                val start = TaskRules.parseTime(task.start)
                val end = TaskRules.parseTime(task.end)

                fun issue(code: PlanIssueCode, field: String) {
                    issues += PlanIssue(code, dayIndex, taskIndex, field)
                }

                if (id.isEmpty()) issue(PlanIssueCode.ID_REQUIRED, "id")
                if (TaskRules.codePointLength(id) > MAX_ID_LENGTH) issue(PlanIssueCode.ID_TOO_LONG, "id")
                if (id.isNotEmpty() && !seenIds.add(id)) issue(PlanIssueCode.DUPLICATE_ID, "id")
                if (title.isEmpty()) issue(PlanIssueCode.TITLE_REQUIRED, "title")
                if (TaskRules.codePointLength(title) > 120) issue(PlanIssueCode.TITLE_TOO_LONG, "title")
                if (details != null && TaskRules.codePointLength(details) > 1000) {
                    issue(PlanIssueCode.DETAILS_TOO_LONG, "details")
                }
                if (category != null && TaskRules.codePointLength(category) > 40) {
                    issue(PlanIssueCode.CATEGORY_TOO_LONG, "category")
                }
                if (start == null) issue(PlanIssueCode.INVALID_START, "start")
                if (end == null) issue(PlanIssueCode.INVALID_END, "end")
                if (start != null && end != null && end <= start) {
                    issue(PlanIssueCode.END_NOT_AFTER_START, "end")
                }
                if (listOfNotNull(id, title, details, category).any(::containsUnsafeControlCharacter)) {
                    issue(PlanIssueCode.INVALID_TEXT, "text")
                }
                if (date != null && id.isNotEmpty() && title.isNotEmpty() && start != null &&
                    end != null && end > start
                ) {
                    validated += ValidatedPlanTask(id, date, title, details, start, end, category)
                }
            }
        }

        val dates = validated.map { it.date }
        if (dates.isNotEmpty() && ChronoUnit.DAYS.between(dates.min(), dates.max()) > 365) {
            issues += PlanIssue(PlanIssueCode.DATE_SPAN_LIMIT)
        }
        return if (issues.isEmpty()) {
            PlanDecodeResult.Success(ValidatedPlan(file.timezone, validated, contentHash))
        } else {
            PlanDecodeResult.Failure(issues.distinct())
        }
    }

    private fun decodeUtf8(bytes: ByteArray): String? = runCatching {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrNull()

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

    private fun containsUnsafeControlCharacter(value: String): Boolean =
        value.any { it.code < 0x20 && it !in listOf('\n', '\r', '\t') }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }
}

data class PlanImportPreview(
    val plan: ValidatedPlan?,
    val errors: List<PlanIssue>,
    val totalTasks: Int,
    val newTasks: Int,
    val exactDuplicates: Int,
    val idConflicts: Int,
    val overlapWarnings: Int,
    val timezoneDiffers: Boolean,
) {
    val canImport: Boolean get() = plan != null && errors.isEmpty() && idConflicts == 0
}

data class PlanImportResult(val imported: Int, val skippedDuplicates: Int)

class PlanImportService(private val database: AppDatabase) {
    private val dao = database.appDao()

    suspend fun preview(bytes: ByteArray, deviceTimeZone: ZoneId): PlanImportPreview {
        val decoded = PlanCodec.decode(bytes)
        if (decoded is PlanDecodeResult.Failure) {
            return PlanImportPreview(null, decoded.issues, 0, 0, 0, 0, 0, false)
        }
        val plan = (decoded as PlanDecodeResult.Success).plan
        val existingImported = dao.getImportedTasks().associateBy { it.externalId }
        val existingAll = if (plan.tasks.isEmpty()) emptyList() else dao.getRange(
            plan.firstDate.toString(),
            plan.lastDate.toString(),
        )
        var duplicates = 0
        var conflicts = 0
        plan.tasks.forEach { incoming ->
            existingImported[incoming.externalId]?.let { current ->
                if (sameContent(current, incoming)) duplicates++ else conflicts++
            }
        }
        var overlaps = 0
        plan.tasks.forEachIndexed { index, incoming ->
            overlaps += plan.tasks.drop(index + 1).count { other ->
                incoming.date == other.date && TaskRules.overlaps(
                    incoming.startMinutes,
                    incoming.endMinutes,
                    other.startMinutes,
                    other.endMinutes,
                )
            }
            overlaps += existingAll.count { current ->
                current.task.date == incoming.date.toString() &&
                    current.task.externalId != incoming.externalId && TaskRules.overlaps(
                        incoming.startMinutes,
                        incoming.endMinutes,
                        current.task.plannedStartMinutes,
                        current.task.plannedEndMinutes,
                    )
            }
        }
        val conflictIssues = if (conflicts > 0) {
            listOf(PlanIssue(PlanIssueCode.EXISTING_ID_CONFLICT, detail = conflicts.toString()))
        } else {
            emptyList()
        }
        return PlanImportPreview(
            plan = plan,
            errors = conflictIssues,
            totalTasks = plan.tasks.size,
            newTasks = plan.tasks.size - duplicates - conflicts,
            exactDuplicates = duplicates,
            idConflicts = conflicts,
            overlapWarnings = overlaps,
            timezoneDiffers = plan.timezone != deviceTimeZone.id,
        )
    }

    suspend fun import(preview: PlanImportPreview, sourceFileName: String? = null): PlanImportResult {
        require(preview.canImport) { "Plan preview is not importable" }
        val plan = requireNotNull(preview.plan)
        val now = System.currentTimeMillis()
        val batchId = UUID.randomUUID().toString()
        var imported = 0
        var skipped = 0
        database.withTransaction {
            val current = dao.getImportedTasks().associateBy { it.externalId }
            val toInsert = plan.tasks.filter { incoming ->
                val existing = current[incoming.externalId]
                when {
                    existing == null -> true
                    sameContent(existing, incoming) -> {
                        skipped++
                        false
                    }
                    else -> error("External ID conflict detected during import")
                }
            }
            if (toInsert.isNotEmpty()) {
                dao.insertBatch(
                    ImportBatchEntity(
                        id = batchId,
                        schemaVersion = PlanCodec.SCHEMA_VERSION,
                        format = PlanCodec.FORMAT,
                        importedAt = now,
                        contentHash = plan.contentHash,
                        taskCount = toInsert.size,
                        sourceFileName = sourceFileName?.take(128),
                    ),
                )
            }
            toInsert.forEach { item ->
                val taskId = UUID.randomUUID().toString()
                dao.insertTask(
                    ScheduleTaskEntity(
                        id = taskId,
                        date = item.date.toString(),
                        title = item.title,
                        details = item.details,
                        plannedStartMinutes = item.startMinutes,
                        plannedEndMinutes = item.endMinutes,
                        category = item.category,
                        source = TaskSource.IMPORT,
                        externalId = item.externalId,
                        importBatchId = batchId,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                dao.insertExecution(ScheduleRepository.newExecution(taskId, now))
                imported++
            }
        }
        return PlanImportResult(imported, skipped)
    }

    private fun sameContent(existing: ScheduleTaskEntity, incoming: ValidatedPlanTask): Boolean =
        existing.date == incoming.date.toString() && existing.title == incoming.title &&
            existing.details == incoming.details &&
            existing.plannedStartMinutes == incoming.startMinutes &&
            existing.plannedEndMinutes == incoming.endMinutes && existing.category == incoming.category
}
