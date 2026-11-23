package com.myagent.app.network

import com.myagent.app.network.dto.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * 聊天接口抽象，便于在 Repository/ViewModel 中替换实现（如单元测试假实现）。
 */
interface ChatApi {

    /**
     * 发起一次聊天请求，逐段返回模型输出文本；收集取消即停止生成。
     *
     * @param reasoningEffort 思考强度（DeepSeek `reasoning_effort`）：`none`/`low`/`medium`/`high`；
     * 传 null 表示不发送该参数，兼容不支持思考强度的模型。
     * @throws ApiException 网络、HTTP 或解析错误。
     */
    fun chatStream(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        temperature: Double = 0.7,
        reasoningEffort: String? = null,
    ): Flow<String>

    /**
     * 发起一次非流式聊天请求，返回完整回复文本（剧情总结等场景使用）。
     *
     * @param reasoningEffort 思考强度（`none`/`low`/`medium`/`high` 等），null 表示不发送该参数。
     * @throws ApiException 网络、HTTP 或解析错误。
     */
    suspend fun complete(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        temperature: Double = 0.7,
        reasoningEffort: String? = null,
    ): String
}
