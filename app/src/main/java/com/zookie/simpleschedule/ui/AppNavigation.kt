package com.zookie.simpleschedule.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zookie.simpleschedule.R

private const val ROUTE_TODAY = "today"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_DATA = "data"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_EDIT = "edit/{taskId}"

private data class BottomDestination(
    val route: String,
    val label: Int,
    val icon: @Composable () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JianChengApp(
    scheduleViewModel: ScheduleViewModel,
    dataViewModel: DataViewModel,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: ROUTE_TODAY
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val selectedDate by scheduleViewModel.selectedDate.collectAsState()

    LaunchedEffect(scheduleViewModel, resources) {
        scheduleViewModel.events.collect { event ->
            when (event) {
                is ScheduleEvent.StatusChanged -> {
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(R.string.status_updated),
                        actionLabel = resources.getString(R.string.undo),
                    )
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        scheduleViewModel.undoStatus(event.previous)
                    }
                }
                ScheduleEvent.TaskSaved -> {
                    navController.popBackStack()
                    snackbarHostState.showSnackbar(resources.getString(R.string.task_saved))
                }
                ScheduleEvent.TaskDeleted -> {
                    if (navController.currentDestination?.route?.startsWith("edit/") == true) {
                        navController.popBackStack()
                    }
                    snackbarHostState.showSnackbar(resources.getString(R.string.task_deleted))
                }
                ScheduleEvent.AnnotationSaved ->
                    snackbarHostState.showSnackbar(resources.getString(R.string.annotation_saved))
                is ScheduleEvent.OperationFailed -> snackbarHostState.showSnackbar(
                    resources.getString(
                        if (event.error == null) R.string.operation_failed
                        else taskErrorString(event.error),
                    ),
                )
            }
        }
    }

    LaunchedEffect(dataViewModel, resources) {
        dataViewModel.events.collect { event ->
            val message = when (event) {
                is DataEvent.ImportFinished -> resources.getString(
                    R.string.import_finished,
                    event.imported,
                    event.skipped,
                )
                DataEvent.RestoreFinished -> resources.getString(R.string.restore_finished)
                DataEvent.ClearFinished -> resources.getString(R.string.clear_finished)
                DataEvent.DocumentWritten -> resources.getString(R.string.document_written)
                DataEvent.NeedCurrentBackup -> resources.getString(R.string.need_current_backup)
                DataEvent.OperationFailed -> resources.getString(R.string.operation_failed)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    val destinations = listOf(
        BottomDestination(ROUTE_TODAY, R.string.nav_today) {
            Icon(Icons.Default.Today, contentDescription = stringResource(R.string.nav_today))
        },
        BottomDestination(ROUTE_HISTORY, R.string.nav_history) {
            Icon(Icons.Default.History, contentDescription = stringResource(R.string.nav_history))
        },
        BottomDestination(ROUTE_DATA, R.string.nav_data) {
            Icon(Icons.Default.Storage, contentDescription = stringResource(R.string.nav_data))
        },
    )
    val showMainChrome = route in destinations.map { it.route }
    val title = when {
        route == ROUTE_TODAY -> R.string.nav_today
        route == ROUTE_HISTORY -> R.string.nav_history
        route == ROUTE_DATA -> R.string.nav_data
        route == ROUTE_SETTINGS -> R.string.settings_privacy
        else -> if (backStack?.arguments?.getString("taskId") == "new") {
            R.string.add_task
        } else {
            R.string.edit_task
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                actions = {
                    if (showMainChrome) {
                        IconButton(onClick = { navController.navigate(ROUTE_SETTINGS) }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings_privacy),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (showMainChrome) {
                NavigationBar {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = route == destination.route,
                            onClick = {
                                if (destination.route == ROUTE_TODAY) scheduleViewModel.today()
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = destination.icon,
                            label = { Text(stringResource(destination.label)) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (route == ROUTE_TODAY) {
                FloatingActionButton(onClick = { navController.navigate("edit/new") }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_task))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_TODAY,
            modifier = Modifier.padding(padding),
        ) {
            composable(ROUTE_TODAY) {
                ScheduleScreen(
                    viewModel = scheduleViewModel,
                    historyMode = false,
                    onEdit = { navController.navigate("edit/$it") },
                )
            }
            composable(ROUTE_HISTORY) {
                ScheduleScreen(
                    viewModel = scheduleViewModel,
                    historyMode = true,
                    onEdit = { navController.navigate("edit/$it") },
                )
            }
            composable(ROUTE_DATA) { DataScreen(dataViewModel) }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    dataViewModel = dataViewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = ROUTE_EDIT,
                arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
            ) { entry ->
                EditTaskScreen(
                    taskId = entry.arguments?.getString("taskId").takeUnless { it == "new" },
                    initialDate = selectedDate,
                    viewModel = scheduleViewModel,
                    onCancel = { navController.popBackStack() },
                )
            }
        }
    }
}

fun taskErrorString(error: com.zookie.simpleschedule.domain.TaskFieldError): Int = when (error) {
    com.zookie.simpleschedule.domain.TaskFieldError.TITLE_REQUIRED -> R.string.error_title_required
    com.zookie.simpleschedule.domain.TaskFieldError.TITLE_TOO_LONG -> R.string.error_title_too_long
    com.zookie.simpleschedule.domain.TaskFieldError.DATE_INVALID -> R.string.error_date_invalid
    com.zookie.simpleschedule.domain.TaskFieldError.START_INVALID -> R.string.error_start_invalid
    com.zookie.simpleschedule.domain.TaskFieldError.END_INVALID -> R.string.error_end_invalid
    com.zookie.simpleschedule.domain.TaskFieldError.END_NOT_AFTER_START -> R.string.error_end_after_start
    com.zookie.simpleschedule.domain.TaskFieldError.DETAILS_TOO_LONG -> R.string.error_details_too_long
    com.zookie.simpleschedule.domain.TaskFieldError.CATEGORY_TOO_LONG -> R.string.error_category_too_long
    com.zookie.simpleschedule.domain.TaskFieldError.ANNOTATION_TOO_LONG -> R.string.error_annotation_too_long
    com.zookie.simpleschedule.domain.TaskFieldError.INVALID_TEXT -> R.string.error_invalid_text
}
