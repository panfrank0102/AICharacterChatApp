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
     * @throws ApiException 网络、HTTP 或解析错误。
     */
    fun chatStream(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        temperature: Double = 0.7,
    ): Flow<String>
}
