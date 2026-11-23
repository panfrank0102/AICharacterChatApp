package com.myagent.app.domain

import com.myagent.app.network.dto.ChatMessage

/**
 * 请求消息组装器。
 *
 * 按需求文档第 4 节的顺序组装一轮请求：
 * 全局提示词（system）→ 会话提示词（system）→ 前情提要（system，剧情增强时）→
 * 命中的世界书条目（system）→ 历史消息（role/content 原样保留）→ 本轮用户输入（user，永远保留）
 * → 后置提示词（system，非空时作为整段消息的最后一条）。
 *
 * 上下文裁剪（需求文档 FR-6 / 第 10 节）按**字符数**（[String.length]，即 UTF-16 码元数）计算：
 * 1. 先从最旧的历史消息开始丢弃，直到总字符数不超过 [contextLimitChars] 或历史清空；
 * 2. 仍超限时，从世界书列表末尾（即调用方排好序后优先级最低的一条）开始丢弃；
 * 3. 全局/会话/前情提要/后置提示词与本轮用户输入永不裁剪，即使只剩它们仍超限也原样返回。
 */
object PromptAssembler {

    private const val ROLE_SYSTEM = "system"
    private const val ROLE_USER = "user"

    /**
     * 组装本轮请求消息列表。
     *
     * @param globalPrompt 全局提示词，空白时跳过
     * @param sessionPrompt 会话提示词，空白时跳过
     * @param postHistoryPrompt 后置提示词，空白时跳过；非空时插入在整段消息的最后
     * @param storyRecap 前情提要（剧情增强），空白时跳过；非空时作为一条 system 插入在会话提示词之后
     * @param worldbookEntries 已由调用方排好序（优先级降序）的世界书条目
     * @param history 历史消息，按时间升序排列
     * @param userInput 本轮用户输入，永远保留、不裁剪
     * @param contextLimitChars 上下文长度预算（字符数）
     * @return 组装并裁剪后的消息列表
     */
    fun assemble(
        globalPrompt: String,
        sessionPrompt: String,
        postHistoryPrompt: String = "",
        storyRecap: String = "",
        worldbookEntries: List<WorldbookMatcher.Entry>,
        history: List<ChatMessage>,
        userInput: String,
        contextLimitChars: Int,
    ): List<ChatMessage> {
        val promptMessages = ArrayList<ChatMessage>(3)
        if (globalPrompt.isNotBlank()) {
            promptMessages.add(ChatMessage(role = ROLE_SYSTEM, content = globalPrompt))
        }
        if (sessionPrompt.isNotBlank()) {
            promptMessages.add(ChatMessage(role = ROLE_SYSTEM, content = sessionPrompt))
        }
        if (storyRecap.isNotBlank()) {
            promptMessages.add(ChatMessage(role = ROLE_SYSTEM, content = storyRecap))
        }

        val worldbookMessages = worldbookEntries.map { entry ->
            ChatMessage(role = ROLE_SYSTEM, content = entry.content)
        }
        val userMessage = ChatMessage(role = ROLE_USER, content = userInput)
        val postHistoryMessage = if (postHistoryPrompt.isNotBlank()) {
            ChatMessage(role = ROLE_SYSTEM, content = postHistoryPrompt)
        } else {
            null
        }

        var totalChars = promptMessages.sumOf { it.content.length } +
            worldbookMessages.sumOf { it.content.length } +
            history.sumOf { it.content.length } +
            userMessage.content.length +
            (postHistoryMessage?.content?.length ?: 0)

        // 先裁历史：history 按时间升序，丢弃前部即从最旧消息开始。
        var historyStart = 0
        while (totalChars > contextLimitChars && historyStart < history.size) {
            totalChars -= history[historyStart].content.length
            historyStart++
        }

        // 再从世界书末尾（优先级最低）开始丢弃。
        var worldbookCount = worldbookMessages.size
        while (totalChars > contextLimitChars && worldbookCount > 0) {
            worldbookCount--
            totalChars -= worldbookMessages[worldbookCount].content.length
        }

        val result = ArrayList<ChatMessage>(
            promptMessages.size + worldbookCount + (history.size - historyStart) + 1 +
                (if (postHistoryMessage != null) 1 else 0),
        )
        result.addAll(promptMessages)
        result.addAll(worldbookMessages.subList(0, worldbookCount))
        for (index in historyStart until history.size) {
            result.add(history[index])
        }
        result.add(userMessage)
        // 后置提示词作为整段消息的最后一条（酒馆 Post-History Instructions 惯例）。
        postHistoryMessage?.let { result.add(it) }
        return result
    }
}
