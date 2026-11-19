package com.myagent.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 世界书条目：会话命中关键词后注入的补充上下文。
 *
 * [keywords] 为多个关键词以换行符分隔；[priority] 越大越靠前，[enabled] 为启用开关。
 */
@Entity(
    tableName = "worldbook_entry",
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
data class WorldBookEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "conversation_id")
    val conversationId: Long,
    val title: String,
    val keywords: String,
    val content: String,
    val priority: Int = 0,
    val enabled: Boolean = true,
)
