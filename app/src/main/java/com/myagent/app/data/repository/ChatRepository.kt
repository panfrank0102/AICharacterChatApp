package com.myagent.app.data.repository

import com.myagent.app.data.db.dao.ConversationDao
import com.myagent.app.data.db.dao.MessageDao
import com.myagent.app.data.db.dao.PresetDao
import com.myagent.app.data.db.dao.WorldBookDao
import com.myagent.app.data.db.entity.MessageEntity
import com.myagent.app.domain.PromptAssembler
import com.myagent.app.domain.WorldbookMatcher
import com.myagent.app.network.ChatApi
import com.myagent.app.network.dto.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * 聊天仓库：编排一轮完整请求。
 *
 * 处理流程（需求文档第 4 节）：
 * 1. 读取会话、全局设置与历史消息；
 * 2. 在需要时落库用户消息，并在标题为空时用输入前 20 字自动生成标题；
 * 3. 解析会话预设（会话未选择时回落到全局默认预设），解密 API Key；
 * 4. 按「本轮输入 + 最近 scanCount 条历史」扫描世界书并递归匹配；
 * 5. 用 [PromptAssembler] 组装消息列表后调用 [ChatApi] 流式请求；
 * 6. 请求成功或中途取消/失败时，把已生成内容作为 AI 消息落库。
 */
class ChatRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val worldBookDao: WorldBookDao,
    private val presetDao: PresetDao,
    private val settings: SettingsRepository,
    private val api: ChatApi,
    private val keyDecryptor: (String) -> String? = { _ -> null },
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** 发送一条用户消息并返回 AI 回复的增量文本流。 */
    fun sendMessage(conversationId: Long, userText: String): Flow<String> =
        startRequest(conversationId, userText, persistUserMessage = true)

    /**
     * 重新生成：丢弃最后一条 AI 回复后，用最后一条用户消息重新请求。
     *
     * @throws IllegalStateException 会话最后一条消息不是用户消息（无可重新生成的内容）时抛出。
     */
    fun regenerate(conversationId: Long): Flow<String> = flow {
        val last = messageDao.getLastMessage(conversationId)
        if (last?.role == ROLE_ASSISTANT) {
            messageDao.deleteLastAssistant(conversationId)
        }
        val lastUser = messageDao.getLastMessage(conversationId)
        if (lastUser == null || lastUser.role != ROLE_USER) {
            throw IllegalStateException("无可重新生成的消息")
        }
        emitAll(startRequest(conversationId, lastUser.content, persistUserMessage = false))
    }

    /**
     * 一轮请求的核心实现。
     *
     * @param persistUserMessage 是否落库用户消息；重新生成时为 false，避免重复插入。
     */
    private fun startRequest(
        conversationId: Long,
        userText: String,
        persistUserMessage: Boolean,
    ): Flow<String> = flow {
        val conversation = conversationDao.getById(conversationId)
            ?: throw IllegalStateException("会话不存在")
        val snapshot = settings.current()

        // 世界书扫描窗口使用「本轮输入 + 最近 scanCount 条历史正文」，因此需在插入用户消息前取历史。
        val historyBefore = messageDao.getByConversation(conversationId)
        val scanHistory = historyBefore.takeLast(conversation.scanCount).map { it.content }

        var insertedMessageId: Long? = null
        if (persistUserMessage) {
            insertedMessageId = messageDao.insert(
                MessageEntity(
                    conversationId = conversationId,
                    role = ROLE_USER,
                    content = userText,
                    createdAt = now(),
                ),
            )
            // 标题为空时自动取用户输入前 20 字；与更新时间合并为一次更新，避免互相覆盖。
            val titled = if (conversation.title.isBlank()) {
                conversation.copy(title = userText.take(20))
            } else {
                conversation
            }
            conversationDao.update(titled.copy(updatedAt = now()))
        }

        // 解析模型预设：会话选择的优先，其次全局默认预设。
        val presetId = conversation.presetId ?: snapshot.defaultPresetId
        val preset = presetId?.let { presetDao.getById(it) }
            ?: throw IllegalStateException("未配置模型预设，请先在会话设置中选择")
        val apiKey = keyDecryptor(preset.apiKeyEncrypted)
            ?: throw IllegalStateException("API Key 解密失败，请重新保存预设")

        // 世界书匹配：启用条目 → 领域匹配（含递归与优先级排序）。
        val entries = worldBookDao.getEnabledByConversation(conversationId).map { entry ->
            WorldbookMatcher.Entry(
                entry.id,
                splitKeywords(entry.keywords),
                entry.content,
                entry.priority,
            )
        }
        val matched = WorldbookMatcher.match(
            userText,
            scanHistory,
            entries,
            conversation.scanCount,
            conversation.recursionDepth,
        )

        // 历史消息排除刚插入的用户消息（本轮输入会作为最后一条 user 追加）；
        // 重新生成（persistUserMessage=false）时，最后一条消息就是本轮输入对应的用户消息，
        // 同样排除，避免请求中出现两条相同的用户消息。
        val excludedId = insertedMessageId
        val historyForPrompt = if (excludedId == null) {
            historyBefore.dropLast(1)
        } else {
            historyBefore.filter { it.id != excludedId }
        }
        val messages = PromptAssembler.assemble(
            globalPrompt = snapshot.globalPrompt,
            sessionPrompt = conversation.systemPrompt,
            worldbookEntries = matched,
            history = historyForPrompt.map { ChatMessage(it.role, it.content) },
            userInput = userText,
            contextLimitChars = snapshot.contextLimitChars,
        )

        // 发送前把 {{user}} 宏替换为会话设置中的用户称呼（角色卡导入的提示词/世界书/开场白保留原样宏）。
        val finalMessages = messages.map { message ->
            message.copy(content = message.content.replace("{{user}}", conversation.userName))
        }

        // 流式请求并逐段转发；无论成功、取消还是失败，只要已有内容就落库。
        val full = StringBuilder()
        var saved = false
        try {
            api.chatStream(
                preset.baseUrl,
                apiKey,
                preset.model,
                finalMessages,
                snapshot.temperature.toDouble(),
                preset.reasoningEffort,
            ).collect { delta ->
                full.append(delta)
                emit(delta)
            }
            if (full.isNotBlank()) {
                messageDao.insert(
                    MessageEntity(
                        conversationId = conversationId,
                        role = ROLE_ASSISTANT,
                        content = full.toString(),
                        createdAt = now(),
                    ),
                )
                saved = true
                val latest = conversationDao.getById(conversationId) ?: conversation
                conversationDao.update(latest.copy(updatedAt = now()))
            }
        } catch (e: CancellationException) {
            persistPartialAnswer(conversationId, full, saved)
            throw e
        } catch (e: Exception) {
            persistPartialAnswer(conversationId, full, saved)
            throw e
        }
    }

    /** 请求中断时把已生成的部分内容落库为一条 AI 消息（已保存过则跳过）。 */
    private suspend fun persistPartialAnswer(
        conversationId: Long,
        full: StringBuilder,
        saved: Boolean,
    ) {
        if (!saved && full.isNotBlank()) {
            messageDao.insert(
                MessageEntity(
                    conversationId = conversationId,
                    role = ROLE_ASSISTANT,
                    content = full.toString(),
                    createdAt = now(),
                ),
            )
        }
    }

    /** 把落库的关键词串按换行/逗号拆分为非空关键词列表。 */
    private fun splitKeywords(raw: String): List<String> =
        raw.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }

    private companion object {
        /** 用户消息的 role 取值。 */
        const val ROLE_USER = "user"

        /** AI 回复消息的 role 取值。 */
        const val ROLE_ASSISTANT = "assistant"
    }
}
