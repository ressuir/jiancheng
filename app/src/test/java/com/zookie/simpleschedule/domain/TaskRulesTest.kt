package com.zookie.simpleschedule.domain

import com.zookie.simpleschedule.data.ChangeSource
import com.zookie.simpleschedule.data.ScheduleTaskEntity
import com.zookie.simpleschedule.data.TaskSource
import com.zookie.simpleschedule.data.TaskStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRulesTest {
    @Test fun blankTitleIsRejected() {
        val result = TaskRules.validate(validInput(title = "  ")) as TaskValidationResult.Invalid
        assertTrue(TaskFieldError.TITLE_REQUIRED in result.errors)
    }

    @Test fun titleLimitCountsUnicodeCodePoints() {
        assertTrue(TaskRules.validate(validInput(title = "😀".repeat(120))) is TaskValidationResult.Valid)
        val result = TaskRules.validate(validInput(title = "😀".repeat(121))) as TaskValidationResult.Invalid
        assertTrue(TaskFieldError.TITLE_TOO_LONG in result.errors)
    }

    @Test fun strictDateAndTimeAreRequired() {
        val result = TaskRules.validate(
            validInput(date = "2026-02-30", start = "9:00", end = "25:00"),
        ) as TaskValidationResult.Invalid
        assertTrue(TaskFieldError.DATE_INVALID in result.errors)
        assertTrue(TaskFieldError.START_INVALID in result.errors)
        assertTrue(TaskFieldError.END_INVALID in result.errors)
    }

    @Test fun endMustBeAfterStartAndCrossMidnightIsRejected() {
        val equal = TaskRules.validate(validInput(start = "10:00", end = "10:00")) as TaskValidationResult.Invalid
        val crossMidnight = TaskRules.validate(validInput(start = "23:00", end = "01:00")) as TaskValidationResult.Invalid
        assertTrue(TaskFieldError.END_NOT_AFTER_START in equal.errors)
        assertTrue(TaskFieldError.END_NOT_AFTER_START in crossMidnight.errors)
    }

    @Test fun overlapUsesHalfOpenIntervals() {
        assertTrue(TaskRules.overlaps(540, 600, 570, 630))
        assertFalse(TaskRules.overlaps(540, 600, 600, 660))
    }

    @Test fun statusTransitionsCanBeCorrectedButNoOpIsRejected() {
        assertTrue(TaskRules.canTransition(TaskStatus.PLANNED, TaskStatus.COMPLETED))
        assertTrue(TaskRules.canTransition(TaskStatus.SKIPPED, TaskStatus.PLANNED))
        assertFalse(TaskRules.canTransition(TaskStatus.PLANNED, TaskStatus.PLANNED))
    }

    @Test fun overdueOnlyAppliesToPastPlannedTasks() {
        val clock = Clock.fixed(Instant.parse("2026-07-20T04:00:00Z"), ZoneId.of("Asia/Shanghai"))
        assertTrue(TaskRules.isOverdueUnresolved(LocalDate.parse("2026-07-20"), 600, TaskStatus.PLANNED, clock))
        assertFalse(TaskRules.isOverdueUnresolved(LocalDate.parse("2026-07-20"), 900, TaskStatus.PLANNED, clock))
        assertFalse(TaskRules.isOverdueUnresolved(LocalDate.parse("2026-07-20"), 600, TaskStatus.COMPLETED, clock))
    }

    @Test fun annotationLimitAndControlsAreValidated() {
        assertEquals(null, TaskRules.validateAnnotation("注".repeat(500)))
        assertEquals(TaskFieldError.ANNOTATION_TOO_LONG, TaskRules.validateAnnotation("注".repeat(501)))
        assertEquals(TaskFieldError.INVALID_TEXT, TaskRules.validateAnnotation("abc\u0000def"))
    }

    @Test fun snapshotPreservesPreviousPlan() {
        val task = ScheduleTaskEntity(
            id = "id",
            date = "2026-07-20",
            title = "旧标题",
            details = "旧说明",
            plannedStartMinutes = 540,
            plannedEndMinutes = 600,
            category = "课程",
            source = TaskSource.MANUAL,
            externalId = null,
            importBatchId = null,
            createdAt = 1,
            updatedAt = 1,
        )
        val revision = TaskRules.snapshot(task, 2, 100, ChangeSource.USER)
        assertEquals("旧标题", revision.previousTitle)
        assertEquals("2026-07-20", revision.previousDate)
        assertEquals(2, revision.revisionNumber)
    }

    @Test fun htmlAndCommandLikeTextRemainPlainData() {
        val value = "<script>alert('x')</script>; rm -rf /"
        val result = TaskRules.validate(validInput(title = value)) as TaskValidationResult.Valid
        assertEquals(value, result.value.title)
    }

    private fun validInput(
        title: String = "学习",
        date: String = "2026-07-20",
        start: String = "09:00",
        end: String = "10:00",
    ) = TaskInput(title, date, start, end)
}

