package com.myagent.app.data.repository

import com.myagent.app.data.db.dao.ConversationDao
import com.myagent.app.data.db.dao.MessageDao
import com.myagent.app.data.db.dao.PresetDao
import com.myagent.app.data.db.dao.WorldBookDao
import com.myagent.app.data.db.entity.MessageEntity
import com.myagent.app.domain.PromptAssembler
import com.myagent.app.domain.StatusBarInstructions
import com.myagent.app.domain.WorldbookMatcher
import com.myagent.app.domain.statusbar.StatusBlockParser
import com.myagent.app.network.ApiException
import com.myagent.app.network.ChatApi
import com.myagent.app.network.dto.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * 聊天仓库：编排一轮完整请求。
 *
 * 普通会话（未开启状态栏优化）：
 * 1. 读取会话、全局设置与历史消息；
 * 2. 在需要时落库用户消息，并在标题为空时用输入前 20 字自动生成标题；
 * 3. 解析会话预设（会话未选择时回落到全局默认预设），解密 API Key；
 * 4. 按「本轮输入 + 最近 scanCount 条历史」扫描世界书并递归匹配；
 * 5. 用 [PromptAssembler] 组装消息列表后调用 [ChatApi] 流式请求；
 * 6. 请求成功或中途取消/失败时，把已生成内容作为 AI 消息落库。
 *
 * 开启「状态栏优化」的会话在正文流式完成后，再发起一次请求专门流式生成状态栏：
 * - 正文请求末尾临时追加"只输出正文"指令（可自定义），正文里的状态栏标记会被剔除；
 * - 状态栏请求在正文后追加 assistant 正文与"只输出状态栏"指令（可自定义），
 *   沿用会话预设参数（可强制关闭思考模式，见 [StatusBarInstructions]）；
 * - 状态栏生成失败时只保留正文，并通过事件提示界面；
 * - 状态栏生成中被停止时，保留已收到的部分状态栏（自动补齐闭合标签）与正文一起落库。
 *
 * @param conversationDao 会话 DAO
 * @param messageDao 消息 DAO
 * @param worldBookDao 世界书 DAO
 * @param presetDao 预设 DAO
 * @param settings 全局设置仓库
 * @param api 聊天接口（流式）
 * @param keyDecryptor API Key 解密函数，生产环境传 [com.myagent.app.data.prefs.ApiKeyCrypto]::decrypt
 * @param now 时间戳来源，测试中可固定
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

    /** 发送一条用户消息，返回本轮请求的流式事件。 */
    fun sendMessage(conversationId: Long, userText: String): Flow<ChatStreamEvent> =
        startRequest(conversationId, userText, persistUserMessage = true)

    /**
     * 重新生成：丢弃最后一条 AI 回复后，用最后一条用户消息重新请求（同样支持两阶段）。
     *
     * @throws IllegalStateException 会话最后一条消息不是用户消息（无可重新生成的内容）时抛出。
     */
    fun regenerate(conversationId: Long): Flow<ChatStreamEvent> = flow {
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
    ): Flow<ChatStreamEvent> = flow {
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
        val assembled = PromptAssembler.assemble(
            globalPrompt = snapshot.globalPrompt,
            sessionPrompt = conversation.systemPrompt,
            postHistoryPrompt = conversation.postHistoryPrompt,
            worldbookEntries = matched,
            history = historyForPrompt.map { ChatMessage(it.role, it.content) },
            userInput = userText,
            contextLimitChars = snapshot.contextLimitChars,
        )

        // 发送前把 {{user}} 宏替换为会话设置中的用户称呼（角色卡导入的提示词/世界书/开场白保留原样宏）。
        val userName = conversation.userName
        val finalMessages = assembled.map { message ->
            message.copy(content = message.content.replace("{{user}}", userName))
        }

        val temperature = snapshot.temperature.toDouble()

        if (!conversation.statusBarOptimization) {
            // 普通路径：行为与历史版本一致，仅事件包装不同。
            val full = StringBuilder()
            try {
                api.chatStream(
                    preset.baseUrl,
                    apiKey,
                    preset.model,
                    finalMessages,
                    temperature,
                    preset.reasoningEffort,
                ).collect { delta ->
                    full.append(delta)
                    emit(ChatStreamEvent.BodyDelta(delta))
                }
            } catch (e: CancellationException) {
                persistPartial(conversationId, full.toString())
                throw e
            } catch (e: Exception) {
                persistPartial(conversationId, full.toString())
                throw e
            }
            saveAssistant(conversationId, full.toString())
            emit(ChatStreamEvent.Completed)
            return@flow
        }

        // 状态栏优化：第一阶段——正文流式（剔除状态栏标记）。
        val bodyInstruction = StatusBarInstructions
            .resolveBodyInstruction(conversation.statusBodyInstruction)
            .replace("{{user}}", userName)
        val statusInstruction = StatusBarInstructions
            .resolveStatusInstruction(conversation.statusBarInstruction)
            .replace("{{user}}", userName)
        val bodyMessages = finalMessages + ChatMessage(role = ROLE_SYSTEM, content = bodyInstruction)

        val rawBody = StringBuilder()
        var visibleBody = ""
        try {
            api.chatStream(
                preset.baseUrl,
                apiKey,
                preset.model,
                bodyMessages,
                temperature,
                preset.reasoningEffort,
            ).collect { delta ->
                rawBody.append(delta)
                val stripped = StatusBlockParser.stripStatusBlocks(rawBody.toString())
                if (stripped.length > visibleBody.length) {
                    emit(ChatStreamEvent.BodyDelta(stripped.substring(visibleBody.length)))
                    visibleBody = stripped
                }
            }
        } catch (e: CancellationException) {
            persistPartial(conversationId, visibleBody)
            throw e
        } catch (e: Exception) {
            persistPartial(conversationId, visibleBody)
            throw e
        }

        if (visibleBody.isBlank()) {
            emit(ChatStreamEvent.StatusFailed("正文为空，已跳过状态栏生成"))
            emit(ChatStreamEvent.Completed)
            return@flow
        }

        // 第二阶段——状态栏流式。
        emit(ChatStreamEvent.StatusStarted)
        val statusMessages = finalMessages +
            ChatMessage(role = ROLE_ASSISTANT, content = visibleBody) +
            ChatMessage(role = ROLE_SYSTEM, content = statusInstruction)
        val statusEffort = StatusBarInstructions.resolveStatusReasoningEffort(
            preset.reasoningEffort,
            conversation.statusForceNoThinking,
        )

        val rawStatus = StringBuilder()
        var statusFailure: String? = null
        try {
            api.chatStream(
                preset.baseUrl,
                apiKey,
                preset.model,
                statusMessages,
                temperature,
                statusEffort,
            ).collect { delta ->
                rawStatus.append(delta)
                emit(ChatStreamEvent.StatusDelta(delta))
            }
            val block = StatusBlockParser.extractStatusBlock(rawStatus.toString())
            if (block == null) {
                statusFailure = "状态栏更新失败：未能识别状态栏内容"
                saveAssistant(conversationId, visibleBody)
            } else {
                saveAssistant(conversationId, visibleBody + "\n\n" + block)
            }
        } catch (e: CancellationException) {
            // 停止生成：保留已收到的部分状态栏（自动补齐闭合标签）与正文。
            val partial = StatusBlockParser.buildPartialStatusBlock(rawStatus.toString())
            withContext(NonCancellable) {
                saveAssistant(
                    conversationId,
                    visibleBody + if (partial == null) "" else "\n\n" + partial,
                )
            }
            throw e
        } catch (e: Exception) {
            // 状态栏请求失败：只保留正文，丢弃已收到的部分状态栏。
            saveAssistant(conversationId, visibleBody)
            statusFailure = toUserMessage(e)
        }

        statusFailure?.let { emit(ChatStreamEvent.StatusFailed(it)) }
        emit(ChatStreamEvent.Completed)
    }

    /** 把已生成的部分正文落库为一条 AI 消息（空白则跳过；可在已取消的协程中调用）。 */
    private suspend fun persistPartial(conversationId: Long, content: String) {
        if (content.isBlank()) return
        withContext(NonCancellable) {
            saveAssistant(conversationId, content)
        }
    }

    /** 保存一条 AI 回复并刷新会话更新时间。 */
    private suspend fun saveAssistant(conversationId: Long, content: String) {
        if (content.isBlank()) return
        messageDao.insert(
            MessageEntity(
                conversationId = conversationId,
                role = ROLE_ASSISTANT,
                content = content,
                createdAt = now(),
            ),
        )
        val latest = conversationDao.getById(conversationId) ?: return
        conversationDao.update(latest.copy(updatedAt = now()))
    }

    /** 把落库的关键词串按换行/逗号拆分为非空关键词列表。 */
    private fun splitKeywords(raw: String): List<String> =
        raw.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }

    /** 把异常转换成面向用户的中文提示。 */
    private fun toUserMessage(e: Throwable): String = when (e) {
        is ApiException -> ApiException.userMessage(e.kind)
        is IllegalStateException -> e.message ?: "操作失败"
        else -> "发生未知错误"
    }

    private companion object {
        /** 用户消息的 role 取值。 */
        const val ROLE_USER = "user"

        /** AI 回复消息的 role 取值。 */
        const val ROLE_ASSISTANT = "assistant"

        /** 系统消息的 role 取值。 */
        const val ROLE_SYSTEM = "system"
    }
}
