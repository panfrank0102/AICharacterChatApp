package com.myagent.app.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 聊天消息，role 取值通常为 system、user 或 assistant。
 */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
)

/**
 * OpenAI 兼容 /chat/completions 接口的请求体。
 *
 * [reasoningEffort] 对应 DeepSeek 的 `reasoning_effort` 参数：
 * `none`/`low`/`medium`/`high`，null 时不发送该字段（兼容不支持该参数的模型）。
 */
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = true,
    val temperature: Double,
    @SerialName("reasoning_effort")
    val reasoningEffort: String? = null,
)

/**
 * 流式响应中的单个 SSE 数据块。
 */
@Serializable
data class ChatCompletionChunk(
    val choices: List<ChunkChoice> = emptyList(),
)

/**
 * 流式响应中的候选结果。
 */
@Serializable
data class ChunkChoice(
    val delta: Delta? = null,
)

/**
 * 流式响应中的增量内容。
 */
@Serializable
data class Delta(
    val content: String? = null,
)

/**
 * 非流式完整响应。
 */
@Serializable
data class ChatCompletionResponse(
    val choices: List<ResponseChoice> = emptyList(),
)

/**
 * 非流式响应中的候选结果。
 */
@Serializable
data class ResponseChoice(
    val message: ChatMessage? = null,
)
