package com.zookie.simpleschedule.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanCodecTest {
    @Test fun validPlanIsDecoded() {
        val result = PlanCodec.decode(validJson().encodeToByteArray()) as PlanDecodeResult.Success
        assertEquals(1, result.plan.tasks.size)
        assertEquals("任务-1", result.plan.tasks.single().externalId)
    }

    @Test fun missingRequiredFieldIsRejected() {
        val result = PlanCodec.decode(validJson().replace("\"title\":\"学习\",", "").encodeToByteArray())
        assertIssue(result, PlanIssueCode.JSON_STRUCTURE)
    }

    @Test fun wrongFieldTypeIsRejected() {
        val result = PlanCodec.decode(validJson().replace("\"start\":\"09:00\"", "\"start\":900").encodeToByteArray())
        assertIssue(result, PlanIssueCode.JSON_STRUCTURE)
    }

    @Test fun unknownFieldIsRejected() {
        val result = PlanCodec.decode(validJson().replace("\"category\":\"课程\"", "\"category\":\"课程\",\"titel\":\"拼错\"").encodeToByteArray())
        assertIssue(result, PlanIssueCode.JSON_STRUCTURE)
    }

    @Test fun unknownSchemaIsRejected() {
        val result = PlanCodec.decode(validJson().replace("\"schemaVersion\":1", "\"schemaVersion\":2").encodeToByteArray())
        assertIssue(result, PlanIssueCode.UNKNOWN_SCHEMA)
    }

    @Test fun duplicateExternalIdIsRejected() {
        val task = "{\"id\":\"same\",\"title\":\"A\",\"start\":\"09:00\",\"end\":\"10:00\"}"
        val json = "{\"format\":\"jiancheng.plan\",\"schemaVersion\":1,\"timezone\":\"Asia/Shanghai\",\"days\":[{\"date\":\"2026-07-20\",\"tasks\":[$task,$task]}]}"
        assertIssue(PlanCodec.decode(json.encodeToByteArray()), PlanIssueCode.DUPLICATE_ID)
    }

    @Test fun oversizedFileIsRejectedBeforeParsing() {
        assertIssue(PlanCodec.decode(ByteArray(PlanCodec.MAX_BYTES + 1)), PlanIssueCode.FILE_TOO_LARGE)
    }

    @Test fun taskCountLimitIsEnforced() {
        val task = PlanTask("id", "title", start = "09:00", end = "10:00")
        val file = PlanFile(PlanCodec.FORMAT, 1, "Asia/Shanghai", listOf(PlanDay("2026-07-20", List(2001) { task.copy(id = "id-$it") })))
        assertIssue(PlanCodec.validate(file), PlanIssueCode.TASK_LIMIT)
    }

    @Test fun dateSpanLimitIsEnforced() {
        val file = PlanFile(
            PlanCodec.FORMAT,
            1,
            "Asia/Shanghai",
            listOf(
                PlanDay("2026-01-01", listOf(PlanTask("a", "A", start = "09:00", end = "10:00"))),
                PlanDay("2027-01-02", listOf(PlanTask("b", "B", start = "09:00", end = "10:00"))),
            ),
        )
        assertIssue(PlanCodec.validate(file), PlanIssueCode.DATE_SPAN_LIMIT)
    }

    @Test fun malformedUtf8IsRejected() {
        assertIssue(PlanCodec.decode(byteArrayOf(0xC3.toByte(), 0x28)), PlanIssueCode.INVALID_UTF8)
    }

    @Test fun scriptAndCommandStringsAreNotInterpreted() {
        val dangerous = "<script>alert(1)</script> && rm -rf /"
        val result = PlanCodec.decode(validJson().replace("学习", dangerous).encodeToByteArray()) as PlanDecodeResult.Success
        assertEquals(dangerous, result.plan.tasks.single().title)
    }

    private fun assertIssue(result: PlanDecodeResult, code: PlanIssueCode) {
        val failure = result as PlanDecodeResult.Failure
        assertTrue(failure.issues.any { it.code == code })
    }

    private fun validJson() = """
        {
          "format":"jiancheng.plan",
          "schemaVersion":1,
          "timezone":"Asia/Shanghai",
          "days":[{"date":"2026-07-20","tasks":[{
            "id":"任务-1","title":"学习","details":"说明",
            "start":"09:00","end":"10:00","category":"课程"
          }]}]
        }
    """.trimIndent()
}
