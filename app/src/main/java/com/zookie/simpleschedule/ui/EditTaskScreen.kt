package com.zookie.simpleschedule.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.zookie.simpleschedule.R
import com.zookie.simpleschedule.domain.TaskInput
import com.zookie.simpleschedule.domain.TaskRules
import java.time.LocalDate

@Composable
fun EditTaskScreen(
    taskId: String?,
    initialDate: LocalDate,
    viewModel: ScheduleViewModel,
    onCancel: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(initialDate.toString()) }
    var start by remember { mutableStateOf("09:00") }
    var end by remember { mutableStateOf("10:00") }
    var details by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var loaded by remember(taskId) { mutableStateOf(taskId == null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val feedback by viewModel.editorFeedback.collectAsState()

    LaunchedEffect(taskId) {
        viewModel.clearEditorFeedback()
        if (taskId != null) {
            val item = viewModel.taskForEdit(taskId)
            if (item != null) {
                title = item.task.title
                date = item.task.date
                start = TaskRules.formatMinutes(item.task.plannedStartMinutes)
                end = TaskRules.formatMinutes(item.task.plannedEndMinutes)
                details = item.task.details.orEmpty()
                category = item.task.category.orEmpty()
            }
            loaded = true
        }
    }

    fun currentInput() = TaskInput(title, date, start, end, details, category)

    if (!loaded) {
        Column(Modifier.fillMaxSize().padding(24.dp)) { Text(stringResource(R.string.loading)) }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.task_title)) },
            supportingText = { Text(stringResource(R.string.character_count, TaskRules.codePointLength(title), 120)) },
            modifier = Modifier.fillMaxWidth().testTag("title_input"),
            singleLine = true,
        )
        OutlinedTextField(
            value = date,
            onValueChange = { date = it },
            label = { Text(stringResource(R.string.date_iso)) },
            modifier = Modifier.fillMaxWidth().testTag("date_input"),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = start,
                onValueChange = { start = it },
                label = { Text(stringResource(R.string.start_time)) },
                modifier = Modifier.weight(1f).testTag("start_input"),
                singleLine = true,
            )
            OutlinedTextField(
                value = end,
                onValueChange = { end = it },
                label = { Text(stringResource(R.string.end_time)) },
                modifier = Modifier.weight(1f).testTag("end_input"),
                singleLine = true,
            )
        }
        OutlinedTextField(
            value = details,
            onValueChange = { details = it },
            label = { Text(stringResource(R.string.task_details)) },
            supportingText = { Text(stringResource(R.string.character_count, TaskRules.codePointLength(details), 1000)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
        OutlinedTextField(
            value = category,
            onValueChange = { category = it },
            label = { Text(stringResource(R.string.category)) },
            supportingText = { Text(stringResource(R.string.character_count, TaskRules.codePointLength(category), 40)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            stringArrayResource(R.array.default_categories).forEach { suggestion ->
                AssistChip(onClick = { category = suggestion }, label = { Text(suggestion) })
            }
        }
        (feedback as? EditorFeedback.ValidationFailed)?.let { invalid ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                invalid.errors.distinct().forEach { error ->
                    Text(
                        text = stringResource(taskErrorString(error)),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.cancel))
            }
            Button(
                onClick = { viewModel.saveTask(taskId, currentInput()) },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.save)) }
        }
        if (taskId != null) {
            TextButton(onClick = { showDeleteConfirm = true }) {
                Text(stringResource(R.string.delete_task))
            }
        }
    }

    if (feedback == EditorFeedback.OverlapConfirmationRequired) {
        AlertDialog(
            onDismissRequest = viewModel::clearEditorFeedback,
            title = { Text(stringResource(R.string.overlap_title)) },
            text = { Text(stringResource(R.string.overlap_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearEditorFeedback()
                    viewModel.saveTask(taskId, currentInput(), allowOverlap = true)
                }) { Text(stringResource(R.string.save_anyway)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::clearEditorFeedback) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (showDeleteConfirm && taskId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.confirm_delete_message, title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTask(taskId)
                    showDeleteConfirm = false
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
