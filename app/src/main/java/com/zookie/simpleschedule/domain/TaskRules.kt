package com.zookie.simpleschedule.domain

import com.zookie.simpleschedule.data.ChangeSource
import com.zookie.simpleschedule.data.ScheduleTaskEntity
import com.zookie.simpleschedule.data.TaskRevisionEntity
import com.zookie.simpleschedule.data.TaskStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.UUID

data class TaskInput(
    val title: String,
    val date: String,
    val start: String,
    val end: String,
    val details: String = "",
    val category: String = "",
)

enum class TaskFieldError {
    TITLE_REQUIRED,
    TITLE_TOO_LONG,
    DATE_INVALID,
    START_INVALID,
    END_INVALID,
    END_NOT_AFTER_START,
    DETAILS_TOO_LONG,
    CATEGORY_TOO_LONG,
    ANNOTATION_TOO_LONG,
    INVALID_TEXT,
}

data class ValidatedTaskInput(
    val title: String,
    val date: LocalDate,
    val startMinutes: Int,
    val endMinutes: Int,
    val details: String?,
    val category: String?,
)

sealed interface TaskValidationResult {
    data class Valid(val value: ValidatedTaskInput) : TaskValidationResult
    data class Invalid(val errors: List<TaskFieldError>) : TaskValidationResult
}

object TaskRules {
    private val dateFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd")
        .withResolverStyle(ResolverStyle.STRICT)
    private val timePattern = Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$")

    fun codePointLength(value: String): Int = value.codePointCount(0, value.length)

    fun validate(input: TaskInput): TaskValidationResult {
        val errors = mutableListOf<TaskFieldError>()
        val title = input.title.trim()
        val details = input.details.trim().ifEmpty { null }
        val category = input.category.trim().ifEmpty { null }
        val date = parseDate(input.date)
        val start = parseTime(input.start)
        val end = parseTime(input.end)

        if (title.isEmpty()) errors += TaskFieldError.TITLE_REQUIRED
        if (codePointLength(title) > 120) errors += TaskFieldError.TITLE_TOO_LONG
        if (date == null) errors += TaskFieldError.DATE_INVALID
        if (start == null) errors += TaskFieldError.START_INVALID
        if (end == null) errors += TaskFieldError.END_INVALID
        if (start != null && end != null && end <= start) {
            errors += TaskFieldError.END_NOT_AFTER_START
        }
        if (details != null && codePointLength(details) > 1000) {
            errors += TaskFieldError.DETAILS_TOO_LONG
        }
        if (category != null && codePointLength(category) > 40) {
            errors += TaskFieldError.CATEGORY_TOO_LONG
        }
        if (listOfNotNull(title, details, category).any(::containsUnsafeControlCharacter)) {
            errors += TaskFieldError.INVALID_TEXT
        }

        return if (errors.isEmpty()) {
            TaskValidationResult.Valid(
                ValidatedTaskInput(
                    title = title,
                    date = requireNotNull(date),
                    startMinutes = requireNotNull(start),
                    endMinutes = requireNotNull(end),
                    details = details,
                    category = category,
                ),
            )
        } else {
            TaskValidationResult.Invalid(errors.distinct())
        }
    }

    fun validateAnnotation(value: String): TaskFieldError? = when {
        codePointLength(value.trim()) > 500 -> TaskFieldError.ANNOTATION_TOO_LONG
        containsUnsafeControlCharacter(value) -> TaskFieldError.INVALID_TEXT
        else -> null
    }

    fun parseDate(value: String): LocalDate? = runCatching {
        LocalDate.parse(value.trim(), dateFormatter)
    }.getOrNull()

    fun parseTime(value: String): Int? {
        val normalized = value.trim()
        if (!timePattern.matches(normalized)) return null
        val (hour, minute) = normalized.split(':').map(String::toInt)
        return hour * 60 + minute
    }

    fun formatMinutes(value: Int): String = "%02d:%02d".format(value / 60, value % 60)

    fun overlaps(startA: Int, endA: Int, startB: Int, endB: Int): Boolean =
        startA < endB && startB < endA

    fun isOverdueUnresolved(
        date: LocalDate,
        endMinutes: Int,
        status: TaskStatus,
        clock: Clock = Clock.systemDefaultZone(),
    ): Boolean {
        if (status != TaskStatus.PLANNED) return false
        val end = date.atStartOfDay(clock.zone).plusMinutes(endMinutes.toLong()).toInstant()
        return Instant.now(clock).isAfter(end)
    }

    fun canTransition(from: TaskStatus, to: TaskStatus): Boolean = from != to

    fun snapshot(
        task: ScheduleTaskEntity,
        revisionNumber: Int,
        changedAt: Long,
        changeSource: ChangeSource = ChangeSource.USER,
    ): TaskRevisionEntity = TaskRevisionEntity(
        id = UUID.randomUUID().toString(),
        taskId = task.id,
        revisionNumber = revisionNumber,
        previousTitle = task.title,
        previousDetails = task.details,
        previousDate = task.date,
        previousStartMinutes = task.plannedStartMinutes,
        previousEndMinutes = task.plannedEndMinutes,
        previousCategory = task.category,
        changedAt = changedAt,
        changeSource = changeSource,
    )

    private fun containsUnsafeControlCharacter(value: String): Boolean =
        value.any { character -> character.code < 0x20 && character !in listOf('\n', '\r', '\t') }
}
