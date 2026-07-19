package com.zookie.simpleschedule.ui

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zookie.simpleschedule.MainActivity
import com.zookie.simpleschedule.data.AppDatabase
import com.zookie.simpleschedule.data.PlanImportPreview
import com.zookie.simpleschedule.data.ScheduleRepository
import com.zookie.simpleschedule.data.ValidatedPlan
import com.zookie.simpleschedule.data.ValidatedPlanTask
import com.zookie.simpleschedule.ui.theme.JianChengTheme
import java.time.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeComponentTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var database: AppDatabase
    private lateinit var repository: ScheduleRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = ScheduleRepository(database)
    }

    @After fun tearDown() {
        database.close()
    }

    @Test fun validImportPreviewShowsCountsAndConfirmation() {
        val dataViewModel = DataViewModel(database, repository).apply {
            importPreview.value = PlanImportPreview(
                plan = ValidatedPlan(
                    timezone = "Asia/Shanghai",
                    tasks = listOf(
                        ValidatedPlanTask(
                            externalId = "preview-1",
                            date = LocalDate.of(2026, 7, 20),
                            title = "预览任务",
                            details = null,
                            startMinutes = 9 * 60,
                            endMinutes = 10 * 60,
                            category = "课程",
                        ),
                    ),
                    contentHash = "preview-hash",
                ),
                errors = emptyList(),
                totalTasks = 1,
                newTasks = 1,
                exactDuplicates = 0,
                idConflicts = 0,
                overlapWarnings = 0,
                timezoneDiffers = false,
            )
        }

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                JianChengTheme { DataScreen(dataViewModel) }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("导入预览").fetchSemanticsNode()
        composeRule.onNodeWithText("确认导入").fetchSemanticsNode()
        composeRule.onNodeWithText("时区：Asia/Shanghai").fetchSemanticsNode()
    }

    @Test fun darkThemeRendersPrimaryTodayText() {
        val scheduleViewModel = ScheduleViewModel(repository)
        val dataViewModel = DataViewModel(database, repository)
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                JianChengTheme(darkTheme = true) {
                    JianChengApp(
                        scheduleViewModel = scheduleViewModel,
                        dataViewModel = dataViewModel,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("今天", useUnmergedTree = true).fetchSemanticsNode()
        composeRule.onNodeWithText("这一天还没有安排。点击“添加”创建第一个时间段。")
            .fetchSemanticsNode()
    }
}
