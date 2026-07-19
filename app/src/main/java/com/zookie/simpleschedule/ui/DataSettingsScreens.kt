package com.zookie.simpleschedule.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.zookie.simpleschedule.BuildConfig
import com.zookie.simpleschedule.R
import com.zookie.simpleschedule.data.AnalysisExportOptions
import com.zookie.simpleschedule.data.BackupIssue
import com.zookie.simpleschedule.data.BackupIssueCode
import com.zookie.simpleschedule.data.PlanIssue
import com.zookie.simpleschedule.data.PlanIssueCode
import com.zookie.simpleschedule.domain.TaskRules
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun DataScreen(viewModel: DataViewModel) {
    val context = LocalContext.current
    val importPreview by viewModel.importPreview.collectAsState()
    val backupPreview by viewModel.backupPreview.collectAsState()
    val pendingDocument by viewModel.pendingDocument.collectAsState()
    val working by viewModel.working.collectAsState()
    var startDate by remember { mutableStateOf(LocalDate.now().minusDays(6).toString()) }
    var endDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var includeTitle by remember { mutableStateOf(true) }
    var includeDetails by remember { mutableStateOf(false) }
    var includeAnnotation by remember { mutableStateOf(false) }
    var includeRevisions by remember { mutableStateOf(true) }
    var categories by remember { mutableStateOf("") }
    var showAnalysisSummary by remember { mutableStateOf(false) }
    var restoreSecondConfirmation by remember { mutableStateOf(false) }

    val planPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.readPlan(context.contentResolver, it) }
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.readBackup(context.contentResolver, it) }
    }
    val documentCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> viewModel.writeDocument(context.contentResolver, uri) }

    LaunchedEffect(pendingDocument?.id) {
        pendingDocument?.let { documentCreator.launch(it.fileName) }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (working) CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        DataActionCard(
            title = stringResource(R.string.import_plan),
            explanation = stringResource(R.string.import_plan_explanation),
            button = stringResource(R.string.choose_plan_file),
            enabled = !working,
            onClick = { planPicker.launch(arrayOf("application/json", "text/plain")) },
        )
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.export_analysis), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.export_analysis_explanation))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        label = { Text(stringResource(R.string.start_date)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = endDate,
                        onValueChange = { endDate = it },
                        label = { Text(stringResource(R.string.end_date)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                ExportSwitch(R.string.include_title, includeTitle, "include_title") { includeTitle = it }
                ExportSwitch(R.string.include_details, includeDetails, "include_details") { includeDetails = it }
                ExportSwitch(R.string.include_annotation, includeAnnotation, "include_annotation") { includeAnnotation = it }
                ExportSwitch(R.string.include_revisions, includeRevisions, "include_revisions") { includeRevisions = it }
                OutlinedTextField(
                    value = categories,
                    onValueChange = { categories = it },
                    label = { Text(stringResource(R.string.category_filter)) },
                    supportingText = { Text(stringResource(R.string.category_filter_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    enabled = !working && TaskRules.parseDate(startDate) != null &&
                        TaskRules.parseDate(endDate) != null,
                    onClick = { showAnalysisSummary = true },
                ) { Text(stringResource(R.string.review_export)) }
            }
        }
        DataActionCard(
            title = stringResource(R.string.export_backup),
            explanation = stringResource(R.string.export_backup_explanation),
            button = stringResource(R.string.choose_save_location),
            enabled = !working,
            onClick = viewModel::prepareBackup,
        )
        DataActionCard(
            title = stringResource(R.string.restore_backup),
            explanation = stringResource(R.string.restore_backup_explanation),
            button = stringResource(R.string.choose_backup_file),
            enabled = !working,
            onClick = { backupPicker.launch(arrayOf("application/json", "text/plain")) },
        )
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.privacy_data_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.privacy_data_summary))
            }
        }
    }

    importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::dismissImportPreview,
            title = { Text(stringResource(R.string.import_preview)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(stringResource(R.string.plan_format_value, "jiancheng.plan", 1))
                    preview.plan?.let {
                        Text(stringResource(R.string.timezone_value, it.timezone))
                        Text(stringResource(R.string.date_range_value, it.firstDate ?: "—", it.lastDate ?: "—"))
                    }
                    Text(stringResource(R.string.preview_counts, preview.totalTasks, preview.newTasks, preview.exactDuplicates, preview.idConflicts, preview.overlapWarnings))
                    if (preview.timezoneDiffers) Text(
                        stringResource(R.string.timezone_warning),
                        color = MaterialTheme.colorScheme.error,
                    )
                    preview.errors.forEach { issue -> Text(planIssueText(issue)) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = preview.canImport,
                    onClick = viewModel::confirmPlanImport,
                ) { Text(stringResource(R.string.confirm_import)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissImportPreview) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showAnalysisSummary) {
        val start = TaskRules.parseDate(startDate)
        val end = TaskRules.parseDate(endDate)
        AlertDialog(
            onDismissRequest = { showAnalysisSummary = false },
            title = { Text(stringResource(R.string.export_summary)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.date_range_value, startDate, endDate))
                    Text(stringResource(R.string.export_fields_summary, includeTitle, includeDetails, includeAnnotation, includeRevisions))
                    Text(stringResource(R.string.export_filename_preview, "jiancheng-analysis-$startDate-$endDate.json"))
                    Text(stringResource(R.string.save_location_next))
                }
            },
            confirmButton = {
                TextButton(
                    enabled = start != null && end != null && !end.isBefore(start),
                    onClick = {
                        viewModel.prepareAnalysis(
                            AnalysisExportOptions(
                                startDate = requireNotNull(start),
                                endDate = requireNotNull(end),
                                includeTitle = includeTitle,
                                includeDetails = includeDetails,
                                includeAnnotation = includeAnnotation,
                                includeRevisions = includeRevisions,
                                categories = categories.split(',').map(String::trim)
                                    .filter(String::isNotEmpty).toSet(),
                            ),
                        )
                        showAnalysisSummary = false
                    },
                ) { Text(stringResource(R.string.choose_save_location)) }
            },
            dismissButton = {
                TextButton(onClick = { showAnalysisSummary = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    backupPreview?.takeUnless { restoreSecondConfirmation }?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::dismissBackupPreview,
            title = { Text(stringResource(R.string.restore_preview)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.restore_counts, preview.taskCount, preview.annotationCount, preview.revisionCount))
                    Text(stringResource(R.string.date_range_value, preview.firstDate ?: "—", preview.lastDate ?: "—"))
                    preview.issues.forEach { Text(backupIssueText(it)) }
                    Text(stringResource(R.string.restore_replace_warning))
                }
            },
            confirmButton = {
                TextButton(
                    enabled = preview.canRestore,
                    onClick = { restoreSecondConfirmation = true },
                ) { Text(stringResource(R.string.continue_action)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissBackupPreview) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (restoreSecondConfirmation) {
        AlertDialog(
            onDismissRequest = { restoreSecondConfirmation = false },
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = { Text(stringResource(R.string.restore_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    restoreSecondConfirmation = false
                    viewModel.confirmRestore()
                }) { Text(stringResource(R.string.replace_and_restore)) }
            },
            dismissButton = {
                TextButton(onClick = { restoreSecondConfirmation = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun DataActionCard(
    title: String,
    explanation: String,
    button: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(explanation)
            Button(enabled = enabled, onClick = onClick) { Text(button) }
        }
    }
}

@Composable
private fun ExportSwitch(
    label: Int,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(label))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
        )
    }
}

@Composable
fun SettingsScreen(
    dataViewModel: DataViewModel,
    onBack: () -> Unit,
) {
    var clearFirst by remember { mutableStateOf(false) }
    var clearSecond by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        SettingSection(R.string.device_timezone, ZoneId.systemDefault().id)
        SettingSection(R.string.local_storage_title, stringResource(R.string.local_storage_description))
        SettingSection(R.string.auto_backup_title, stringResource(R.string.auto_backup_disabled))
        SettingSection(R.string.uninstall_title, stringResource(R.string.uninstall_description))
        SettingSection(R.string.format_title, stringResource(R.string.format_description))
        SettingSection(R.string.app_version, BuildConfig.VERSION_NAME)
        SettingSection(R.string.open_source_licenses, stringResource(R.string.license_description))
        HorizontalDivider()
        TextButton(onClick = { clearFirst = true }) {
            Text(stringResource(R.string.clear_all_data), color = MaterialTheme.colorScheme.error)
        }
    }
    if (clearFirst) {
        AlertDialog(
            onDismissRequest = { clearFirst = false },
            title = { Text(stringResource(R.string.clear_confirm_title)) },
            text = { Text(stringResource(R.string.clear_confirm_first)) },
            confirmButton = {
                TextButton(onClick = { clearFirst = false; clearSecond = true }) {
                    Text(stringResource(R.string.continue_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { clearFirst = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (clearSecond) {
        AlertDialog(
            onDismissRequest = { clearSecond = false },
            title = { Text(stringResource(R.string.clear_confirm_second_title)) },
            text = { Text(stringResource(R.string.clear_confirm_second)) },
            confirmButton = {
                TextButton(onClick = {
                    clearSecond = false
                    dataViewModel.clearAll()
                }) { Text(stringResource(R.string.clear_all_data)) }
            },
            dismissButton = {
                TextButton(onClick = { clearSecond = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SettingSection(title: Int, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(title), fontWeight = FontWeight.Bold)
        Text(value)
    }
}

@Composable
private fun planIssueText(issue: PlanIssue): String {
    val location = when {
        issue.dayIndex != null && issue.taskIndex != null -> stringResource(
            R.string.issue_task_location,
            issue.dayIndex + 1,
            issue.taskIndex + 1,
        )
        issue.dayIndex != null -> stringResource(R.string.issue_day_location, issue.dayIndex + 1)
        else -> ""
    }
    val message = stringResource(
        when (issue.code) {
            PlanIssueCode.FILE_TOO_LARGE -> R.string.issue_file_too_large
            PlanIssueCode.INVALID_UTF8 -> R.string.issue_invalid_utf8
            PlanIssueCode.NESTING_TOO_DEEP -> R.string.issue_nesting
            PlanIssueCode.JSON_STRUCTURE -> R.string.issue_json_structure
            PlanIssueCode.WRONG_FORMAT -> R.string.issue_wrong_plan_format
            PlanIssueCode.UNKNOWN_SCHEMA -> R.string.issue_unknown_schema
            PlanIssueCode.INVALID_TIMEZONE -> R.string.issue_timezone
            PlanIssueCode.INVALID_DATE -> R.string.issue_date
            PlanIssueCode.ID_REQUIRED -> R.string.issue_id_required
            PlanIssueCode.ID_TOO_LONG -> R.string.issue_id_too_long
            PlanIssueCode.DUPLICATE_ID -> R.string.issue_duplicate_id
            PlanIssueCode.TITLE_REQUIRED -> R.string.error_title_required
            PlanIssueCode.TITLE_TOO_LONG -> R.string.error_title_too_long
            PlanIssueCode.DETAILS_TOO_LONG -> R.string.error_details_too_long
            PlanIssueCode.CATEGORY_TOO_LONG -> R.string.error_category_too_long
            PlanIssueCode.INVALID_START -> R.string.error_start_invalid
            PlanIssueCode.INVALID_END -> R.string.error_end_invalid
            PlanIssueCode.END_NOT_AFTER_START -> R.string.error_end_after_start
            PlanIssueCode.TASK_LIMIT -> R.string.issue_task_limit
            PlanIssueCode.DATE_SPAN_LIMIT -> R.string.issue_date_span
            PlanIssueCode.INVALID_TEXT -> R.string.error_invalid_text
            PlanIssueCode.EXISTING_ID_CONFLICT -> R.string.issue_existing_conflict
        },
    )
    return "$location$message${issue.detail?.let { " ($it)" }.orEmpty()}"
}

@Composable
private fun backupIssueText(issue: BackupIssue): String {
    val location = issue.taskIndex?.let { stringResource(R.string.issue_backup_task, it + 1) }.orEmpty()
    val message = stringResource(
        when (issue.code) {
            BackupIssueCode.FILE_TOO_LARGE -> R.string.issue_backup_large
            BackupIssueCode.INVALID_UTF8 -> R.string.issue_invalid_utf8
            BackupIssueCode.NESTING_TOO_DEEP -> R.string.issue_nesting
            BackupIssueCode.JSON_STRUCTURE -> R.string.issue_json_structure
            BackupIssueCode.WRONG_FORMAT -> R.string.issue_wrong_backup_format
            BackupIssueCode.UNKNOWN_SCHEMA -> R.string.issue_unknown_schema
            BackupIssueCode.INVALID_TIMEZONE -> R.string.issue_timezone
            BackupIssueCode.TASK_LIMIT -> R.string.issue_backup_task_limit
            BackupIssueCode.INVALID_TASK -> R.string.issue_backup_task_invalid
            BackupIssueCode.DUPLICATE_ID -> R.string.issue_backup_duplicate
            BackupIssueCode.INVALID_EXECUTION -> R.string.issue_backup_execution
            BackupIssueCode.INVALID_ANNOTATION -> R.string.issue_backup_annotation
            BackupIssueCode.INVALID_REVISION -> R.string.issue_backup_revision
            BackupIssueCode.INVALID_IMPORT_BATCH -> R.string.issue_backup_batch_invalid
            BackupIssueCode.MISSING_IMPORT_BATCH -> R.string.issue_backup_batch
        },
    )
    return "$location$message${issue.detail?.let { " ($it)" }.orEmpty()}"
}
