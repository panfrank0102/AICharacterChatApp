package com.myagent.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.myagent.app.data.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

/** 消息的数据访问接口。 */
@Dao
interface MessageDao {

    @Query("SELECT * FROM message WHERE conversation_id = :conversationId ORDER BY id ASC")
    fun observeByConversation(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM message WHERE conversation_id = :conversationId ORDER BY id ASC")
    suspend fun getByConversation(conversationId: Long): List<MessageEntity>

    @Insert
    suspend fun insert(entity: MessageEntity): Long

    @Query("DELETE FROM message WHERE conversation_id = :conversationId AND id >= :messageId")
    suspend fun deleteByIdAndLater(conversationId: Long, messageId: Long)

    @Query(
        "DELETE FROM message WHERE id = (SELECT MAX(id) FROM message " +
            "WHERE conversation_id = :conversationId AND role = 'assistant')"
    )
    suspend fun deleteLastAssistant(conversationId: Long)

    @Query("SELECT * FROM message WHERE conversation_id = :conversationId ORDER BY id DESC LIMIT 1")
    suspend fun getLastMessage(conversationId: Long): MessageEntity?

    /** 更新一条消息的剧情总结与分支选项（剧情增强功能）。 */
    @Query("UPDATE message SET summary = :summary, branches = :branches WHERE id = :id")
    suspend fun updateSummary(id: Long, summary: String, branches: String)
}
