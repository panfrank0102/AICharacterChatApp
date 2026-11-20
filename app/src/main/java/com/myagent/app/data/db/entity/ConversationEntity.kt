package com.myagent.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 会话实体：一个独立聊天，拥有自己的消息、世界书、提示词与模型预设。
 *
 * 删除会话时会级联删除其消息与世界书条目。
 */
@Entity(tableName = "conversation")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    @ColumnInfo(name = "preset_id")
    val presetId: Long? = null,
    @ColumnInfo(name = "system_prompt")
    val systemPrompt: String = "",
    @ColumnInfo(name = "scan_count")
    val scanCount: Int = 20,
    @ColumnInfo(name = "recursion_depth")
    val recursionDepth: Int = 2,
    /** 发送消息时替换 `{{user}}` 宏所用的用户称呼。 */
    @ColumnInfo(name = "user_name")
    val userName: String = "用户",
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
