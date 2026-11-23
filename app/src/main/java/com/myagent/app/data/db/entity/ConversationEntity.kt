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
    /** 后置提示词：每轮请求中插在对话历史之后（整段消息最后一条 system）的补充指令。 */
    @ColumnInfo(name = "post_history_prompt")
    val postHistoryPrompt: String = "",
    @ColumnInfo(name = "scan_count")
    val scanCount: Int = 20,
    @ColumnInfo(name = "recursion_depth")
    val recursionDepth: Int = 2,
    /** 发送消息时替换 `{{user}}` 宏所用的用户称呼。 */
    @ColumnInfo(name = "user_name")
    val userName: String = "用户",
    /** 是否开启「状态栏优化」：正文与状态栏分两次请求分别流式生成。 */
    @ColumnInfo(name = "status_bar_optimization")
    val statusBarOptimization: Boolean = false,
    /** 状态栏优化·正文请求的附加指令；空串表示使用内置默认指令。 */
    @ColumnInfo(name = "status_body_instruction")
    val statusBodyInstruction: String = "",
    /** 状态栏优化·状态栏请求的附加指令；空串表示使用内置默认指令。 */
    @ColumnInfo(name = "status_bar_instruction")
    val statusBarInstruction: String = "",
    /** 状态栏优化·是否强制状态栏请求关闭思考模式（默认关闭，沿用预设）。 */
    @ColumnInfo(name = "status_force_no_thinking")
    val statusForceNoThinking: Boolean = false,
    /** 剧情增强：创建会话/导入角色卡时决定，创建后不可更改。 */
    @ColumnInfo(name = "story_enhancement")
    val storyEnhancement: Boolean = false,
    /** 剧情增强·剧情分支选择：总结请求同时返回四个备选选项（可随时修改）。 */
    @ColumnInfo(name = "story_branches")
    val storyBranches: Boolean = false,
    /** 剧情增强·每次请求发送的最近对话轮次（1~10，默认 2；可随时修改）。 */
    @ColumnInfo(name = "story_rounds")
    val storyRounds: Int = 2,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
