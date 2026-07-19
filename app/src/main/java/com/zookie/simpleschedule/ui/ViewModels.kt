package com.zookie.simpleschedule.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zookie.simpleschedule.data.AnalysisExportOptions
import com.zookie.simpleschedule.data.AnalysisExportService
import com.zookie.simpleschedule.data.AnnotationSaveResult
import com.zookie.simpleschedule.data.AppDatabase
import com.zookie.simpleschedule.data.BackupCodec
import com.zookie.simpleschedule.data.BackupPreview
import com.zookie.simpleschedule.data.BackupService
import com.zookie.simpleschedule.data.PlanCodec
import com.zookie.simpleschedule.data.PlanImportPreview
import com.zookie.simpleschedule.data.PlanImportService
import com.zookie.simpleschedule.data.SaveTaskResult
import com.zookie.simpleschedule.data.ScheduleRepository
import com.zookie.simpleschedule.data.TaskDetail
import com.zookie.simpleschedule.data.TaskExecutionEntity
import com.zookie.simpleschedule.data.TaskStatus
import com.zookie.simpleschedule.data.TaskWithExecution
import com.zookie.simpleschedule.domain.TaskFieldError
import com.zookie.simpleschedule.domain.TaskInput
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ScheduleUiState {
    data object Loading : ScheduleUiState
    data class Ready(val tasks: List<TaskWithExecution>) : ScheduleUiState
    data object Error : ScheduleUiState
}

sealed interface ScheduleEvent {
    data class StatusChanged(val previous: TaskExecutionEntity) : ScheduleEvent
    data object TaskSaved : ScheduleEvent
    data object TaskDeleted : ScheduleEvent
    data object AnnotationSaved : ScheduleEvent
    data class OperationFailed(val error: TaskFieldError? = null) : ScheduleEvent
}

sealed interface EditorFeedback {
    data class ValidationFailed(val errors: List<TaskFieldError>) : EditorFeedback
    data object OverlapConfirmationRequired : EditorFeedback
}

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModel(private val repository: ScheduleRepository) : ViewModel() {
    val selectedDate = MutableStateFlow(LocalDate.now())
    val schedule: StateFlow<ScheduleUiState> = selectedDate
        .flatMapLatest { repository.observeDate(it) }
        .map<List<TaskWithExecution>, ScheduleUiState> { ScheduleUiState.Ready(it) }
        .catch { emit(ScheduleUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUiState.Loading)

    private val eventChannel = Channel<ScheduleEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private val _detail = MutableStateFlow<TaskDetail?>(null)
    val detail: StateFlow<TaskDetail?> = _detail
    val editorFeedback = MutableStateFlow<EditorFeedback?>(null)

    fun previousDay() { selectedDate.value = selectedDate.value.minusDays(1) }
    fun nextDay() { selectedDate.value = selectedDate.value.plusDays(1) }
    fun today() { selectedDate.value = LocalDate.now() }
    fun setDate(value: LocalDate) { selectedDate.value = value }

    suspend fun taskForEdit(id: String): TaskWithExecution? = repository.getTask(id)

    fun saveTask(id: String?, input: TaskInput, allowOverlap: Boolean = false) {
        viewModelScope.launch {
            val result = if (id == null) repository.createTask(input, allowOverlap)
            else repository.editTask(id, input, allowOverlap)
            when (result) {
                is SaveTaskResult.Success -> {
                    editorFeedback.value = null
                    eventChannel.send(ScheduleEvent.TaskSaved)
                }
                is SaveTaskResult.Invalid ->
                    editorFeedback.value = EditorFeedback.ValidationFailed(result.errors)
                SaveTaskResult.OverlapConfirmationRequired ->
                    editorFeedback.value = EditorFeedback.OverlapConfirmationRequired
                SaveTaskResult.NotFound -> eventChannel.send(ScheduleEvent.OperationFailed())
            }
        }
    }

    fun deleteTask(id: String) {
        viewModelScope.launch {
            if (repository.deleteTask(id)) eventChannel.send(ScheduleEvent.TaskDeleted)
            else eventChannel.send(ScheduleEvent.OperationFailed())
        }
    }

    fun updateStatus(id: String, status: TaskStatus) {
        viewModelScope.launch {
            val previous = repository.updateStatus(id, status)
            if (previous == null) eventChannel.send(ScheduleEvent.OperationFailed())
            else eventChannel.send(ScheduleEvent.StatusChanged(previous))
        }
    }

    fun undoStatus(previous: TaskExecutionEntity) {
        viewModelScope.launch { repository.restoreExecution(previous) }
    }

    fun saveAnnotation(id: String, text: String) {
        viewModelScope.launch {
            when (val result = repository.saveAnnotation(id, text)) {
                AnnotationSaveResult.Success -> eventChannel.send(ScheduleEvent.AnnotationSaved)
                is AnnotationSaveResult.Invalid -> eventChannel.send(ScheduleEvent.OperationFailed(result.error))
                AnnotationSaveResult.NotFound -> eventChannel.send(ScheduleEvent.OperationFailed())
            }
        }
    }

    fun showDetail(id: String) {
        viewModelScope.launch { _detail.value = repository.getDetail(id) }
    }

    fun closeDetail() { _detail.value = null }
    fun clearEditorFeedback() { editorFeedback.value = null }
}

enum class DocumentKind { ANALYSIS, BACKUP }

data class PreparedDocument(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val content: String,
    val kind: DocumentKind,
)

sealed interface DataEvent {
    data class ImportFinished(val imported: Int, val skipped: Int) : DataEvent
    data object RestoreFinished : DataEvent
    data object ClearFinished : DataEvent
    data object DocumentWritten : DataEvent
    data object NeedCurrentBackup : DataEvent
    data object OperationFailed : DataEvent
}

class DataViewModel(
    private val database: AppDatabase,
    private val repository: ScheduleRepository,
) : ViewModel() {
    private val planService = PlanImportService(database)
    private val backupService = BackupService(database)
    private val analysisService = AnalysisExportService(database)

    val importPreview = MutableStateFlow<PlanImportPreview?>(null)
    val backupPreview = MutableStateFlow<BackupPreview?>(null)
    val pendingDocument = MutableStateFlow<PreparedDocument?>(null)
    val working = MutableStateFlow(false)
    val currentBackupExportedThisSession = MutableStateFlow(false)

    private val eventChannel = Channel<DataEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    fun readPlan(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            working.value = true
            importPreview.value = runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = readSelectedDocument(resolver, uri, PlanCodec.MAX_BYTES + 1)
                    planService.preview(bytes, ZoneId.systemDefault())
                }
            }.getOrElse {
                eventChannel.send(DataEvent.OperationFailed)
                null
            }
            working.value = false
        }
    }

    fun confirmPlanImport() {
        val preview = importPreview.value ?: return
        viewModelScope.launch {
            working.value = true
            runCatching { withContext(Dispatchers.IO) { planService.import(preview) } }
                .onSuccess {
                    importPreview.value = null
                    eventChannel.send(DataEvent.ImportFinished(it.imported, it.skippedDuplicates))
                }
                .onFailure { eventChannel.send(DataEvent.OperationFailed) }
            working.value = false
        }
    }

    fun dismissImportPreview() { importPreview.value = null }

    fun prepareBackup() {
        viewModelScope.launch {
            working.value = true
            runCatching { withContext(Dispatchers.IO) { backupService.export() } }
                .onSuccess { content ->
                    pendingDocument.value = PreparedDocument(
                        fileName = "jiancheng-backup-${LocalDate.now()}.json",
                        content = content,
                        kind = DocumentKind.BACKUP,
                    )
                }
                .onFailure { eventChannel.send(DataEvent.OperationFailed) }
            working.value = false
        }
    }

    fun prepareAnalysis(options: AnalysisExportOptions) {
        viewModelScope.launch {
            working.value = true
            runCatching { withContext(Dispatchers.IO) { analysisService.export(options) } }
                .onSuccess { content ->
                    pendingDocument.value = PreparedDocument(
                        fileName = "jiancheng-analysis-${options.startDate}-${options.endDate}.json",
                        content = content,
                        kind = DocumentKind.ANALYSIS,
                    )
                }
                .onFailure { eventChannel.send(DataEvent.OperationFailed) }
            working.value = false
        }
    }

    fun writeDocument(resolver: ContentResolver, uri: Uri?) {
        val document = pendingDocument.value ?: return
        if (uri == null) {
            pendingDocument.value = null
            return
        }
        viewModelScope.launch {
            working.value = true
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    resolver.openOutputStream(uri, "wt")?.bufferedWriter(StandardCharsets.UTF_8)?.use {
                        it.write(document.content)
                    } ?: error("Unable to open selected destination")
                }.isSuccess
            }
            if (success) {
                if (document.kind == DocumentKind.BACKUP) {
                    currentBackupExportedThisSession.value = true
                }
                eventChannel.send(DataEvent.DocumentWritten)
            } else {
                eventChannel.send(DataEvent.OperationFailed)
            }
            pendingDocument.value = null
            working.value = false
        }
    }

    fun readBackup(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            working.value = true
            backupPreview.value = runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = readSelectedDocument(resolver, uri, BackupCodec.MAX_BYTES + 1)
                    backupService.preview(bytes)
                }
            }.getOrElse {
                eventChannel.send(DataEvent.OperationFailed)
                null
            }
            working.value = false
        }
    }

    fun confirmRestore() {
        val preview = backupPreview.value ?: return
        val backup = preview.backup ?: return
        viewModelScope.launch {
            val hasCurrentData = withContext(Dispatchers.IO) { !repository.isEmpty() }
            if (hasCurrentData && !currentBackupExportedThisSession.value) {
                eventChannel.send(DataEvent.NeedCurrentBackup)
                return@launch
            }
            working.value = true
            runCatching { withContext(Dispatchers.IO) { backupService.restore(backup) } }
                .onSuccess {
                    backupPreview.value = null
                    currentBackupExportedThisSession.value = false
                    eventChannel.send(DataEvent.RestoreFinished)
                }
                .onFailure { eventChannel.send(DataEvent.OperationFailed) }
            working.value = false
        }
    }

    fun dismissBackupPreview() { backupPreview.value = null }

    fun clearAll() {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.clearAll() } }
                .onSuccess { eventChannel.send(DataEvent.ClearFinished) }
                .onFailure { eventChannel.send(DataEvent.OperationFailed) }
        }
    }

    private suspend fun readSelectedDocument(
        resolver: ContentResolver,
        uri: Uri,
        maximumBytes: Int,
    ): ByteArray = withContext(Dispatchers.IO) {
        resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream(minOf(maximumBytes, 64 * 1024))
            val buffer = ByteArray(8192)
            var total = 0
            while (total < maximumBytes) {
                val read = input.read(buffer, 0, minOf(buffer.size, maximumBytes - total))
                if (read < 0) break
                output.write(buffer, 0, read)
                total += read
            }
            output.toByteArray()
        } ?: error("Unable to open selected document")
    }
}

class AppViewModelFactory(
    private val database: AppDatabase,
    private val repository: ScheduleRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(ScheduleViewModel::class.java) -> ScheduleViewModel(repository) as T
        modelClass.isAssignableFrom(DataViewModel::class.java) -> DataViewModel(database, repository) as T
        else -> error("Unknown ViewModel class: ${modelClass.name}")
    }
}
