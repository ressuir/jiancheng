package com.zookie.simpleschedule.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.zookie.simpleschedule.R
import com.zookie.simpleschedule.data.TaskDetail
import com.zookie.simpleschedule.data.TaskStatus
import com.zookie.simpleschedule.data.TaskWithExecution
import com.zookie.simpleschedule.domain.TaskRules
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    historyMode: Boolean,
    onEdit: (String) -> Unit,
) {
    val date by viewModel.selectedDate.collectAsState()
    val state by viewModel.schedule.collectAsState()
    val detail by viewModel.detail.collectAsState()
    var annotationTask by remember { mutableStateOf<TaskWithExecution?>(null) }
    var deleteTask by remember { mutableStateOf<TaskWithExecution?>(null) }
    var jumpDate by remember(date) { mutableStateOf(date.toString()) }

    Column(modifier = Modifier.fillMaxSize()) {
        DateHeader(
            date = date,
            historyMode = historyMode,
            jumpDate = jumpDate,
            onJumpDateChange = { jumpDate = it },
            onJump = { TaskRules.parseDate(jumpDate)?.let(viewModel::setDate) },
            onPrevious = viewModel::previousDay,
            onNext = viewModel::nextDay,
            onToday = viewModel::today,
        )
        when (val current = state) {
            ScheduleUiState.Loading -> EmptyMessage(stringResource(R.string.loading))
            ScheduleUiState.Error -> EmptyMessage(stringResource(R.string.load_failed))
            is ScheduleUiState.Ready -> if (current.tasks.isEmpty()) {
                EmptyMessage(
                    stringResource(
                        if (historyMode) R.string.history_empty else R.string.today_empty,
                    ),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(current.tasks, key = { it.task.id }) { item ->
                        TaskCard(
                            item = item,
                            onToggleComplete = {
                                viewModel.updateStatus(
                                    item.task.id,
                                    if (item.execution.status == TaskStatus.COMPLETED) {
                                        TaskStatus.PLANNED
                                    } else {
                                        TaskStatus.COMPLETED
                                    },
                                )
                            },
                            onStatus = { viewModel.updateStatus(item.task.id, it) },
                            onAnnotation = { annotationTask = item },
                            onEdit = { onEdit(item.task.id) },
                            onDelete = { deleteTask = item },
                            onDetail = { viewModel.showDetail(item.task.id) },
                        )
                    }
                }
            }
        }
    }

    annotationTask?.let { item ->
        AnnotationDialog(
            initial = item.execution.annotation.orEmpty(),
            onDismiss = { annotationTask = null },
            onSave = {
                viewModel.saveAnnotation(item.task.id, it)
                annotationTask = null
            },
        )
    }
    deleteTask?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTask = null },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.confirm_delete_message, item.task.title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTask(item.task.id)
                    deleteTask = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTask = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    detail?.let {
        TaskDetailDialog(detail = it, onDismiss = viewModel::closeDetail)
    }
}

@Composable
private fun DateHeader(
    date: LocalDate,
    historyMode: Boolean,
    jumpDate: String,
    onJumpDateChange: (String) -> Unit,
    onJump: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val formatter = remember {
        DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(date.format(formatter), style = MaterialTheme.typography.titleLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onPrevious) { Text(stringResource(R.string.previous_day)) }
            TextButton(onClick = onToday) { Text(stringResource(R.string.back_today)) }
            TextButton(onClick = onNext) { Text(stringResource(R.string.next_day)) }
        }
        if (historyMode) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = jumpDate,
                    onValueChange = onJumpDateChange,
                    label = { Text(stringResource(R.string.date_iso)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = onJump) { Text(stringResource(R.string.jump_to_date)) }
            }
        }
    }
}

@Composable
private fun EmptyMessage(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun TaskCard(
    item: TaskWithExecution,
    onToggleComplete: () -> Unit,
    onStatus: (TaskStatus) -> Unit,
    onAnnotation: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDetail: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val now = LocalTime.now().hour * 60 + LocalTime.now().minute
    val today = LocalDate.now().toString()
    val isCurrent = item.task.date == today && now in item.task.plannedStartMinutes until item.task.plannedEndMinutes
    val overdue = TaskRules.isOverdueUnresolved(
        LocalDate.parse(item.task.date),
        item.task.plannedEndMinutes,
        item.execution.status,
    )
    val toggleDescription = stringResource(
        if (item.execution.status == TaskStatus.COMPLETED) R.string.mark_planned else R.string.mark_complete,
    )
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = item.execution.status == TaskStatus.COMPLETED,
                onCheckedChange = { onToggleComplete() },
                modifier = Modifier.testTag("task_complete_${item.task.id}")
                    .semantics { contentDescription = toggleDescription },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${TaskRules.formatMinutes(item.task.plannedStartMinutes)}–${TaskRules.formatMinutes(item.task.plannedEndMinutes)}",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = item.task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                item.task.category?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium)
                }
                item.execution.annotation?.let {
                    Text(
                        text = it,
                        modifier = Modifier.testTag("annotation_summary"),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(statusString(item.execution.status)),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    if (isCurrent) {
                        Text(stringResource(R.string.task_in_progress), style = MaterialTheme.typography.labelSmall)
                    } else if (overdue) {
                        Text(stringResource(R.string.overdue_unresolved), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            IconButton(onClick = onAnnotation) {
                Icon(Icons.Default.NoteAlt, contentDescription = stringResource(R.string.annotation))
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_actions))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                listOf(
                    TaskStatus.SKIPPED to R.string.status_skipped,
                    TaskStatus.NOT_DONE to R.string.status_not_done,
                    TaskStatus.CANCELED to R.string.status_canceled,
                    TaskStatus.PLANNED to R.string.status_planned,
                ).filter { it.first != item.execution.status }.forEach { (status, label) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        onClick = { menuOpen = false; onStatus(status) },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit)) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = { menuOpen = false; onEdit() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.details)) },
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                    onClick = { menuOpen = false; onDetail() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun AnnotationDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.annotation_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.annotation_explanation))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.annotation)) },
                    supportingText = { Text(stringResource(R.string.character_count, TaskRules.codePointLength(text), 500)) },
                    minLines = 3,
                    modifier = Modifier.testTag("annotation_input"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (initial.isNotEmpty()) {
                    TextButton(onClick = { onSave("") }) { Text(stringResource(R.string.delete_annotation)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun TaskDetailDialog(detail: TaskDetail, onDismiss: () -> Unit) {
    val timestampFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") }
    val none = stringResource(R.string.none)
    fun formatTimestamp(value: Long?): String = value?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(timestampFormatter)
    } ?: none
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(detail.item.task.title) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(stringResource(R.string.current_plan))
                    Text(
                        "${detail.item.task.date}  ${TaskRules.formatMinutes(detail.item.task.plannedStartMinutes)}–${TaskRules.formatMinutes(detail.item.task.plannedEndMinutes)}",
                    )
                    Text(stringResource(R.string.status_with_value, stringResource(statusString(detail.item.execution.status))))
                    Text(stringResource(R.string.status_changed_at, formatTimestamp(detail.item.execution.statusChangedAt)))
                    detail.item.execution.annotation?.let {
                        Text(stringResource(R.string.annotation_with_value, it))
                        Text(stringResource(R.string.annotation_updated_at, formatTimestamp(detail.item.execution.annotationUpdatedAt)))
                    }
                }
                if (detail.revisions.isEmpty()) {
                    item { Text(stringResource(R.string.no_revisions)) }
                } else {
                    item { Text(stringResource(R.string.revision_history), fontWeight = FontWeight.Bold) }
                    items(detail.revisions) { revision ->
                        Text(
                            stringResource(
                                R.string.revision_item,
                                revision.revisionNumber,
                                revision.previousDate,
                                TaskRules.formatMinutes(revision.previousStartMinutes),
                                TaskRules.formatMinutes(revision.previousEndMinutes),
                                revision.previousTitle,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

fun statusString(status: TaskStatus): Int = when (status) {
    TaskStatus.PLANNED -> R.string.status_planned
    TaskStatus.COMPLETED -> R.string.status_completed
    TaskStatus.SKIPPED -> R.string.status_skipped
    TaskStatus.NOT_DONE -> R.string.status_not_done
    TaskStatus.CANCELED -> R.string.status_canceled
}
