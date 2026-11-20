package com.myagent.app.network

import com.myagent.app.network.dto.ChatMessage
import com.myagent.app.network.dto.ChatRequest
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 请求体 JSON 序列化测试：确认思考强度字段按约定写入/省略。
 *
 * 复用 [OPEN_AI_JSON]，保证测试与生产请求的序列化配置一致。
 */
class ChatRequestJsonTest {

    private fun payload(reasoningEffort: String?): String = OPEN_AI_JSON.encodeToString(
        ChatRequest(
            model = "deepseek-chat",
            messages = listOf(ChatMessage(role = "user", content = "你好")),
            stream = true,
            temperature = 0.7,
            reasoningEffort = reasoningEffort,
        ),
    )

    @Test
    fun nullReasoningEffort_isOmittedFromPayload() {
        val json = payload(null)
        assertFalse("不设置思考强度时不应出现 reasoning_effort 字段", json.contains("reasoning_effort"))
        // encodeDefaults = true：stream 默认值仍要显式写入。
        assertTrue("应显式携带 stream=true", json.contains("\"stream\":true"))
    }

    @Test
    fun nonNullReasoningEffort_isWrittenWithSnakeCaseKey() {
        val json = payload("medium")
        assertTrue(
            "应写入 reasoning_effort=medium（snake_case 键名），实际：$json",
            json.contains("\"reasoning_effort\":\"medium\""),
        )
    }

    @Test
    fun eachSupportedEffortValue_serializes() {
        listOf("none", "low", "medium", "high").forEach { effort ->
            val json = payload(effort)
            assertTrue(
                "reasoning_effort=$effort 应写入请求体，实际：$json",
                json.contains("\"reasoning_effort\":\"$effort\""),
            )
        }
    }
}
