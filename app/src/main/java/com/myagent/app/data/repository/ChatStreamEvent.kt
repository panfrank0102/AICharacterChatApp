package com.myagent.app.data.repository

/**
 * 聊天请求的流式事件（状态栏优化功能引入）。
 *
 * 普通会话只会收到 [BodyDelta] 与 [Completed]；
 * 开启「状态栏优化」的会话还会收到 [StatusStarted]、[StatusDelta]，
 * 状态栏生成失败时收到 [StatusFailed]（正文仍会正常保存）。
 */
sealed interface ChatStreamEvent {

    /** 正文流式增量（状态栏优化会话中，正文里的状态栏标记已被剔除）。 */
    data class BodyDelta(val text: String) : ChatStreamEvent

    /** 正文已完成，开始流式生成状态栏。 */
    data object StatusStarted : ChatStreamEvent

    /** 状态栏流式增量（原始文本，可能尚未闭合）。 */
    data class StatusDelta(val text: String) : ChatStreamEvent

    /** 状态栏生成失败：正文已保存，界面可提示用户。 */
    data class StatusFailed(val userMessage: String) : ChatStreamEvent

    /** 剧情总结生成失败：AI 回复已保存（不含总结），界面可轻提示用户。 */
    data class SummaryFailed(val userMessage: String) : ChatStreamEvent

    /** 本轮 AI 消息已全部落库。 */
    data object Completed : ChatStreamEvent
}
