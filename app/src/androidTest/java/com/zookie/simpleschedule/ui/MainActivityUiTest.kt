package com.zookie.simpleschedule.ui

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zookie.simpleschedule.JianChengApplication
import com.zookie.simpleschedule.MainActivity
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Before fun clearData() {
        val app = ApplicationProvider.getApplicationContext<JianChengApplication>()
        runBlocking { app.container.repository.clearAll() }
        composeRule.waitForIdle()
    }

    @Test fun startsOnTodayAndShowsEmptyState() {
        composeRule.onNodeWithText("今天", useUnmergedTree = true).fetchSemanticsNode()
        composeRule.onNodeWithText("这一天还没有安排。点击“添加”创建第一个时间段。").fetchSemanticsNode()
    }

    @Test fun addCompleteAndUndoCoreFlow() {
        addTask("测试任务")
        composeRule.onNodeWithText("测试任务").fetchSemanticsNode()
        composeRule.onNodeWithContentDescription("标记为已完成").performClick()
        composeRule.onNodeWithContentDescription("恢复为待处理").fetchSemanticsNode()
        composeRule.onNodeWithText("撤销").performClick()
        composeRule.onNodeWithContentDescription("标记为已完成").fetchSemanticsNode()
    }

    @Test fun annotationIsOptionalAndCanBeSaved() {
        addTask("批注任务")
        assertEquals(0, composeRule.onAllNodesWithTag("annotation_summary").fetchSemanticsNodes().size)
        composeRule.onNodeWithContentDescription("批注").performClick()
        composeRule.onNodeWithTag("annotation_input").performTextInput("一句简单批注")
        composeRule.onNodeWithText("保存").performClick()
        composeRule.onNodeWithTag("annotation_summary").assertTextContains("一句简单批注")
    }

    @Test fun taskCanBeMarkedSkippedFromMoreMenu() {
        addTask("跳过任务")
        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onNodeWithText("已跳过").performClick()
        composeRule.onNodeWithText("已跳过").fetchSemanticsNode()
    }

    @Test fun historySupportsDateBrowsing() {
        composeRule.onNodeWithText("历史", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("前一天").performClick()
        assertEquals(0, composeRule.onAllNodesWithTag("date_input").fetchSemanticsNodes().size)
        composeRule.onNodeWithText("回到今天").fetchSemanticsNode()
    }

    @Test fun analysisAnnotationOptionDefaultsOff() {
        composeRule.onNodeWithText("数据", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("include_title").assertIsOn()
        composeRule.onNodeWithTag("include_annotation").assertIsOff()
    }

    @Test fun deleteRequiresConfirmation() {
        addTask("待删除")
        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("确认删除任务？").fetchSemanticsNode()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithText("待删除").fetchSemanticsNode()
    }

    private fun addTask(title: String) {
        composeRule.onNodeWithContentDescription("添加任务").performClick()
        composeRule.onNodeWithTag("title_input").performTextInput(title)
        composeRule.onNodeWithTag("date_input").performTextClearance()
        composeRule.onNodeWithTag("date_input").performTextInput(LocalDate.now().toString())
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitForIdle()
    }
}
