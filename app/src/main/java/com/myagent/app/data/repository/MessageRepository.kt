package com.myagent.app.data.repository

import com.myagent.app.data.db.dao.MessageDao
import com.myagent.app.data.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * 消息仓库：负责会话内消息的读取、追加与删除。
 *
 * role 仅使用 "user" 与 "assistant" 两种取值，创建时间取当前时刻。
 */
class MessageRepository(private val dao: MessageDao) {

    /** 观察指定会话的全部消息（按 id 升序）。 */
    fun observeByConversation(id: Long): Flow<List<MessageEntity>> = dao.observeByConversation(id)

    /** 一次性读取指定会话的全部消息。 */
    suspend fun getByConversation(id: Long): List<MessageEntity> = dao.getByConversation(id)

    /** 读取指定会话的最后一条消息，不存在时返回 null。 */
    suspend fun getLastMessage(id: Long): MessageEntity? = dao.getLastMessage(id)

    /** 追加一条用户消息，返回新消息 id。 */
    suspend fun insertUser(conversationId: Long, content: String): Long = dao.insert(
        MessageEntity(
            conversationId = conversationId,
            role = ROLE_USER,
            content = content,
            createdAt = System.currentTimeMillis(),
        ),
    )

    /** 追加一条 AI 回复消息，返回新消息 id。 */
    suspend fun insertAssistant(conversationId: Long, content: String): Long = dao.insert(
        MessageEntity(
            conversationId = conversationId,
            role = ROLE_ASSISTANT,
            content = content,
            createdAt = System.currentTimeMillis(),
        ),
    )

    /** 删除指定消息及其之后（id 更大）的全部消息。 */
    suspend fun deleteFrom(conversationId: Long, messageId: Long) {
        dao.deleteByIdAndLater(conversationId, messageId)
    }

    /** 删除会话中最后一条 AI 回复（用于重新生成）。 */
    suspend fun deleteLastAssistant(conversationId: Long) {
        dao.deleteLastAssistant(conversationId)
    }

    private companion object {
        /** 用户消息的 role 取值。 */
        const val ROLE_USER = "user"

        /** AI 回复消息的 role 取值。 */
        const val ROLE_ASSISTANT = "assistant"
    }
}
