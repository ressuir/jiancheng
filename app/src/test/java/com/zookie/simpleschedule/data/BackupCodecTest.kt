package com.zookie.simpleschedule.data

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    @Test fun validBackupRoundTrips() {
        val file = validBackup()
        val decoded = BackupCodec.decode(BackupCodec.encode(file).encodeToByteArray()) as BackupDecodeResult.Success
        assertEquals(file, decoded.backup.file)
    }

    @Test fun completedTaskRequiresCompletionTime() {
        val task = validBackup().tasks.single()
        val file = validBackup().copy(tasks = listOf(task.copy(execution = task.execution.copy(completedAt = null))))
        val result = BackupCodec.validate(file) as BackupDecodeResult.Failure
        assertTrue(result.issues.any { it.code == BackupIssueCode.INVALID_EXECUTION })
    }

    @Test fun annotationLengthIsValidated() {
        val task = validBackup().tasks.single()
        val file = validBackup().copy(tasks = listOf(task.copy(execution = task.execution.copy(annotation = "注".repeat(501)))))
        val result = BackupCodec.validate(file) as BackupDecodeResult.Failure
        assertTrue(result.issues.any { it.code == BackupIssueCode.INVALID_ANNOTATION })
    }

    @Test fun excessivelyNestedBackupIsRejectedBeforeParsing() {
        val bytes = ("[".repeat(33) + "]".repeat(33)).encodeToByteArray()
        val result = BackupCodec.decode(bytes) as BackupDecodeResult.Failure
        assertTrue(result.issues.any { it.code == BackupIssueCode.NESTING_TOO_DEEP })
    }

    @Test fun malformedImportBatchIsRejected() {
        val batch = BackupImportBatch(
            id = UUID.randomUUID().toString(),
            schemaVersion = 1,
            format = PlanCodec.FORMAT,
            importedAt = 1,
            contentHash = "not-a-sha256",
            taskCount = 1,
        )
        val result = BackupCodec.validate(validBackup().copy(importBatches = listOf(batch)))
            as BackupDecodeResult.Failure
        assertTrue(result.issues.any { it.code == BackupIssueCode.INVALID_IMPORT_BATCH })
    }

    private fun validBackup(): BackupFile {
        val id = UUID.randomUUID().toString()
        return BackupFile(
            format = BackupCodec.FORMAT,
            schemaVersion = 1,
            exportedAt = 100,
            timezone = "Asia/Shanghai",
            tasks = listOf(
                BackupTask(
                    id = id,
                    date = "2026-07-20",
                    title = "学习",
                    plannedStartMinutes = 540,
                    plannedEndMinutes = 600,
                    source = TaskSource.MANUAL,
                    createdAt = 1,
                    updatedAt = 2,
                    execution = BackupExecution(TaskStatus.COMPLETED, 3, completedAt = 3),
                    revisions = emptyList(),
                ),
            ),
            importBatches = emptyList(),
        )
    }
}
