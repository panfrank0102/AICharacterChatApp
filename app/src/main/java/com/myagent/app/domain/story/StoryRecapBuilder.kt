package com.myagent.app.domain.story

/**
 * 剧情增强功能的上下文切分工具（纯函数，可单元测试）。
 */

/** 参与切分的一条历史消息（由仓库从实体映射而来）。 */
data class StoryMessage(val role: String, val content: String, val summary: String)

/** 切分结果：前情提要文本 + 保留原话的窗口消息。 */
data class StoryContext(val recap: String, val window: List<StoryMessage>)

/**
 * 按"发送轮次"把历史切分为「最近 N 轮原话」与「更早历史的剧情总结」。
 *
 * 轮次语义（与需求一致）：N=1 表示保留最近 1 条 AI 回复原话；N=2 表示保留最近
 * 2 条 AI 回复与夹在它们之间的 1 条用户输入原话；以此类推。窗口之外的 AI 回复
 * 原话被丢弃，其剧情总结按时间顺序拼接为一条前情提要（缺少总结的旧消息跳过）。
 */
object StoryRecapBuilder {

    /**
     * 计算前情提要与本轮窗口。
     *
     * @param messages 全部历史消息，按时间升序排列（不含本轮用户输入）。
     * @param rounds 发送轮次（1~10）。
     * @param enhancementEnabled 是否开启剧情增强；未开启时窗口为全部消息、无前情提要。
     * @return 切分结果。
     */
    fun build(
        messages: List<StoryMessage>,
        rounds: Int,
        enhancementEnabled: Boolean,
    ): StoryContext {
        if (!enhancementEnabled || messages.isEmpty()) {
            return StoryContext(recap = "", window = messages)
        }

        // 从尾部向前数出最近 rounds 条 AI 回复，窗口从最早保留的那条 AI 回复开始。
        var assistantCount = 0
        var windowStart = messages.size
        for (index in messages.indices.reversed()) {
            if (messages[index].role == ASSISTANT) {
                assistantCount++
                windowStart = index
                if (assistantCount >= rounds) break
            }
        }

        // 还没有任何 AI 回复（如首轮）时，全部按原话发送。
        if (assistantCount == 0) {
            return StoryContext(recap = "", window = messages)
        }

        val window = messages.subList(windowStart, messages.size)
        val recap = messages.subList(0, windowStart)
            .filter { it.role == ASSISTANT && it.summary.isNotBlank() }
            .joinToString("\n") { it.summary.trim() }
        return StoryContext(recap = recap, window = window)
    }

    private const val ASSISTANT = "assistant"
}
