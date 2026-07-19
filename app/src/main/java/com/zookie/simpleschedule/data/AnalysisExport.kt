package com.zookie.simpleschedule.data

import com.zookie.simpleschedule.domain.TaskRules
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class AnalysisExportOptions(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val includeTitle: Boolean = true,
    val includeDetails: Boolean = false,
    val includeAnnotation: Boolean = false,
    val includeRevisions: Boolean = true,
    val categories: Set<String> = emptySet(),
)

@Serializable
data class AnalysisFile(
    val format: String,
    val schemaVersion: Int,
    val exportedAt: Long,
    val timezone: String,
    val range: AnalysisRange,
    val includedFields: IncludedFields,
    val tasks: List<AnalysisTask>,
)

@Serializable data class AnalysisRange(val startDate: String, val endDate: String)

@Serializable
data class IncludedFields(
    val title: Boolean,
    val details: Boolean,
    val annotation: Boolean,
    val revisions: Boolean,
)

@Serializable
data class AnalysisTask(
    val taskRef: String,
    val category: String? = null,
    val originalPlan: AnalysisPlan,
    val currentPlan: AnalysisPlan,
    val plannedDurationMinutes: Int,
    val execution: AnalysisExecution,
    val planModified: Boolean,
    val revisions: List<AnalysisRevision>? = null,
    val annotation: String? = null,
)

@Serializable
data class AnalysisPlan(
    val date: String,
    val start: String,
    val end: String,
    val title: String? = null,
    val details: String? = null,
)

@Serializable
data class AnalysisRevision(
    val revisionNumber: Int,
    val changedAt: Long,
    val planBeforeChange: AnalysisPlan,
)

@Serializable
data class AnalysisExecution(
    val finalStatus: TaskStatus,
    val statusChangedAt: Long,
    val completedAt: Long? = null,
    val overdueUnresolved: Boolean,
)

object AnalysisCodec {
    const val FORMAT = "jiancheng.analysis"
    const val SCHEMA_VERSION = 1
    private val json = Json {
        prettyPrint = true
        explicitNulls = false
        encodeDefaults = false
        ignoreUnknownKeys = false
    }

    fun encode(file: AnalysisFile): String = json.encodeToString(file)
    fun decode(text: String): AnalysisFile = json.decodeFromString(text)
}

class AnalysisExportService(
    private val database: AppDatabase,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val dao = database.appDao()

    suspend fun export(options: AnalysisExportOptions): String {
        require(!options.endDate.isBefore(options.startDate))
        val items = dao.getRange(options.startDate.toString(), options.endDate.toString())
            .filter { options.categories.isEmpty() || it.task.category in options.categories }
        val tasks = items.mapIndexed { index, item ->
            val revisions = dao.getRevisions(item.task.id)
            val original = revisions.minByOrNull { it.revisionNumber }
            fun plan(
                date: String,
                start: Int,
                end: Int,
                title: String,
                details: String?,
            ) = AnalysisPlan(
                date = date,
                start = TaskRules.formatMinutes(start),
                end = TaskRules.formatMinutes(end),
                title = title.takeIf { options.includeTitle },
                details = details.takeIf { options.includeDetails },
            )
            AnalysisTask(
                taskRef = "T${(index + 1).toString().padStart(3, '0')}",
                category = item.task.category,
                originalPlan = if (original == null) {
                    plan(
                        item.task.date,
                        item.task.plannedStartMinutes,
                        item.task.plannedEndMinutes,
                        item.task.title,
                        item.task.details,
                    )
                } else {
                    plan(
                        original.previousDate,
                        original.previousStartMinutes,
                        original.previousEndMinutes,
                        original.previousTitle,
                        original.previousDetails,
                    )
                },
                currentPlan = plan(
                    item.task.date,
                    item.task.plannedStartMinutes,
                    item.task.plannedEndMinutes,
                    item.task.title,
                    item.task.details,
                ),
                plannedDurationMinutes = item.task.plannedEndMinutes - item.task.plannedStartMinutes,
                execution = AnalysisExecution(
                    finalStatus = item.execution.status,
                    statusChangedAt = item.execution.statusChangedAt,
                    completedAt = item.execution.completedAt,
                    overdueUnresolved = TaskRules.isOverdueUnresolved(
                        LocalDate.parse(item.task.date),
                        item.task.plannedEndMinutes,
                        item.execution.status,
                        clock,
                    ),
                ),
                planModified = revisions.isNotEmpty(),
                revisions = if (options.includeRevisions) revisions.map { revision ->
                    AnalysisRevision(
                        revisionNumber = revision.revisionNumber,
                        changedAt = revision.changedAt,
                        planBeforeChange = plan(
                            revision.previousDate,
                            revision.previousStartMinutes,
                            revision.previousEndMinutes,
                            revision.previousTitle,
                            revision.previousDetails,
                        ),
                    )
                } else null,
                annotation = item.execution.annotation.takeIf { options.includeAnnotation },
            )
        }
        return AnalysisCodec.encode(
            AnalysisFile(
                format = AnalysisCodec.FORMAT,
                schemaVersion = AnalysisCodec.SCHEMA_VERSION,
                exportedAt = clock.millis(),
                timezone = clock.zone.id,
                range = AnalysisRange(options.startDate.toString(), options.endDate.toString()),
                includedFields = IncludedFields(
                    title = options.includeTitle,
                    details = options.includeDetails,
                    annotation = options.includeAnnotation,
                    revisions = options.includeRevisions,
                ),
                tasks = tasks,
            ),
        )
    }
}
