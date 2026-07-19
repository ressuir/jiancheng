package com.zookie.simpleschedule.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ScheduleRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        repository = ScheduleRepository(
            database,
            Clock.fixed(Instant.parse("2026-07-20T02:00:00Z"), ZoneId.of("Asia/Shanghai")),
        )
    }

    @After fun tearDown() { database.close() }

    @Test fun addSortEditStatusAnnotationAndCascadeDelete() = runTest {
        val later = repository.createTask(taskInput("稍后", "11:00", "12:00")) as SaveTaskResult.Success
        val earlier = repository.createTask(taskInput("先做", "09:00", "10:00")) as SaveTaskResult.Success
        val ordered = repository.observeDate(java.time.LocalDate.parse("2026-07-20")).first()
        assertEquals(listOf("先做", "稍后"), ordered.map { it.task.title })

        repository.editTask(
            earlier.taskId,
            taskInput("先做（修订）", "09:30", "10:30"),
            allowOverlap = true,
        )
        val detail = repository.getDetail(earlier.taskId)
        assertEquals("先做（修订）", detail?.item?.task?.title)
        assertEquals("先做", detail?.revisions?.single()?.previousTitle)

        repository.updateStatus(earlier.taskId, TaskStatus.COMPLETED)
        repository.saveAnnotation(earlier.taskId, "需要再复习一次")
        val persisted = repository.getTask(earlier.taskId)
        assertEquals(TaskStatus.COMPLETED, persisted?.execution?.status)
        assertNotNull(persisted?.execution?.completedAt)
        assertEquals("需要再复习一次", persisted?.execution?.annotation)

        repository.deleteTask(earlier.taskId)
        assertNull(repository.getTask(earlier.taskId))
        assertTrue(database.appDao().getRevisions(earlier.taskId).isEmpty())
        assertNotNull(repository.getTask(later.taskId))
    }

    @Test fun statusCanBeUndoneExactly() = runTest {
        val id = (repository.createTask(taskInput("任务", "09:00", "10:00")) as SaveTaskResult.Success).taskId
        val previous = requireNotNull(repository.updateStatus(id, TaskStatus.SKIPPED))
        assertEquals(TaskStatus.SKIPPED, repository.getTask(id)?.execution?.status)
        repository.restoreExecution(previous)
        assertEquals(TaskStatus.PLANNED, repository.getTask(id)?.execution?.status)
    }

    @Test fun planImportDetectsExactDuplicateAndIdConflict() = runTest {
        val service = PlanImportService(database)
        val bytes = planJson("原计划").encodeToByteArray()
        val preview = service.preview(bytes, ZoneId.of("Asia/Shanghai"))
        assertTrue(preview.canImport)
        assertEquals(1, service.import(preview).imported)

        val duplicate = service.preview(bytes, ZoneId.of("Asia/Shanghai"))
        assertEquals(1, duplicate.exactDuplicates)
        assertEquals(0, service.import(duplicate).imported)

        val conflict = service.preview(planJson("不同内容").encodeToByteArray(), ZoneId.of("Asia/Shanghai"))
        assertEquals(1, conflict.idConflicts)
        assertTrue(!conflict.canImport)
    }

    @Test fun failedImportTransactionLeavesNoPartialRows() = runTest {
        val sameExternalId = listOf(
            ValidatedPlanTask("duplicate", java.time.LocalDate.parse("2026-07-20"), "A", null, 540, 600, null),
            ValidatedPlanTask("duplicate", java.time.LocalDate.parse("2026-07-20"), "B", null, 660, 720, null),
        )
        val preview = PlanImportPreview(
            plan = ValidatedPlan("Asia/Shanghai", sameExternalId, "hash"),
            errors = emptyList(),
            totalTasks = 2,
            newTasks = 2,
            exactDuplicates = 0,
            idConflicts = 0,
            overlapWarnings = 0,
            timezoneDiffers = false,
        )
        runCatching { PlanImportService(database).import(preview) }
        assertEquals(0, database.appDao().taskCount())
        assertTrue(database.appDao().getImportBatches().isEmpty())
    }

    @Test fun backupRoundTripRestoresTasksExecutionAnnotationAndRevision() = runTest {
        val id = (repository.createTask(taskInput("原任务", "09:00", "10:00")) as SaveTaskResult.Success).taskId
        repository.editTask(id, taskInput("新任务", "09:30", "10:30"))
        repository.updateStatus(id, TaskStatus.COMPLETED)
        repository.saveAnnotation(id, "批注")
        val service = BackupService(database)
        val encoded = service.export(ZoneId.of("Asia/Shanghai"))
        val preview = service.preview(encoded.encodeToByteArray())
        assertTrue(preview.canRestore)
        repository.clearAll()
        service.restore(requireNotNull(preview.backup))
        val restored = repository.getDetail(id)
        assertEquals("新任务", restored?.item?.task?.title)
        assertEquals(TaskStatus.COMPLETED, restored?.item?.execution?.status)
        assertEquals("批注", restored?.item?.execution?.annotation)
        assertEquals(1, restored?.revisions?.size)
    }

    @Test fun analysisExportUsesLocalRefsAndExcludesPrivateFieldsByDefault() = runTest {
        val id = (repository.createTask(taskInput("隐私标题", "09:00", "10:00")) as SaveTaskResult.Success).taskId
        repository.saveAnnotation(id, "默认不应导出的批注")
        val service = AnalysisExportService(database)
        val defaultText = service.export(
            AnalysisExportOptions(
                startDate = java.time.LocalDate.parse("2026-07-20"),
                endDate = java.time.LocalDate.parse("2026-07-20"),
            ),
        )
        val parsed = AnalysisCodec.decode(defaultText)
        assertEquals("T001", parsed.tasks.single().taskRef)
        assertEquals(null, parsed.tasks.single().annotation)
        assertTrue(!defaultText.contains(id))
        assertTrue(!defaultText.contains("Android ID", ignoreCase = true))
        assertTrue(!defaultText.contains("默认不应导出的批注"))

        val includedText = service.export(
            AnalysisExportOptions(
                startDate = java.time.LocalDate.parse("2026-07-20"),
                endDate = java.time.LocalDate.parse("2026-07-20"),
                includeAnnotation = true,
            ),
        )
        assertEquals("默认不应导出的批注", AnalysisCodec.decode(includedText).tasks.single().annotation)
    }

    @Test fun failedRestoreTransactionRollsBackToOriginalData() = runTest {
        val originalId = (repository.createTask(taskInput("保留", "09:00", "10:00")) as SaveTaskResult.Success).taskId
        val duplicateId = UUID.randomUUID().toString()
        val duplicateTask = backupTask(duplicateId)
        val invalidButPrevalidated = ValidatedBackup(
            BackupFile(
                format = BackupCodec.FORMAT,
                schemaVersion = 1,
                exportedAt = 1,
                timezone = "Asia/Shanghai",
                tasks = listOf(duplicateTask, duplicateTask),
                importBatches = emptyList(),
            ),
        )
        runCatching { BackupService(database).restoreValidated(invalidButPrevalidated) }
        assertNotNull(repository.getTask(originalId))
        assertEquals(1, database.appDao().taskCount())
    }

    @Test fun fileDatabasePersistsAcrossReopen() = runTest {
        database.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "restart-${UUID.randomUUID()}.db"
        val first = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2).build()
        val firstRepository = ScheduleRepository(first)
        val id = (firstRepository.createTask(taskInput("重启后仍在", "09:00", "10:00")) as SaveTaskResult.Success).taskId
        first.close()
        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2).build()
        assertEquals("重启后仍在", reopened.appDao().getTask(id)?.task?.title)
        reopened.close()
        context.deleteDatabase(name)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    private fun taskInput(title: String, start: String, end: String) =
        com.zookie.simpleschedule.domain.TaskInput(title, "2026-07-20", start, end)

    private fun planJson(title: String) = """
        {"format":"jiancheng.plan","schemaVersion":1,"timezone":"Asia/Shanghai","days":[
          {"date":"2026-07-20","tasks":[{"id":"external-1","title":"$title","start":"09:00","end":"10:00"}]}
        ]}
    """.trimIndent()

    private fun backupTask(id: String) = BackupTask(
        id = id,
        date = "2026-07-20",
        title = "恢复任务",
        plannedStartMinutes = 540,
        plannedEndMinutes = 600,
        source = TaskSource.MANUAL,
        createdAt = 1,
        updatedAt = 1,
        execution = BackupExecution(TaskStatus.PLANNED, 1),
        revisions = emptyList(),
    )
}
