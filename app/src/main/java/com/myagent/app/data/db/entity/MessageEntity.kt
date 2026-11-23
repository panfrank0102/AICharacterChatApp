package com.myagent.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 消息实体：会话中的一条聊天记录。
 *
 * [role] 仅取 "user" 或 "assistant"；所属会话被删除时级联删除。
 */
@Entity(
    tableName = "message",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conversation_id")],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "conversation_id")
    val conversationId: Long,
    val role: String,
    val content: String,
    /** 剧情总结（剧情增强功能，仅 AI 消息使用）；空串表示未生成。 */
    @ColumnInfo(name = "summary")
    val summary: String = "",
    /** 剧情分支备选选项（剧情增强功能，仅 AI 消息使用）；多个选项按换行符分隔。 */
    @ColumnInfo(name = "branches")
    val branches: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
