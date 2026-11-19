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
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
