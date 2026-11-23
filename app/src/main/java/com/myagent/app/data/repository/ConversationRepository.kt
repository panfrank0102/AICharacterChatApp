package com.myagent.app.data.repository

import com.myagent.app.data.db.dao.ConversationDao
import com.myagent.app.data.db.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

/**
 * 会话仓库：负责会话的新建、重命名、设置更新、排序时间戳维护与删除。
 *
 * 会话标题的自动生成依赖消息内容，因此不在此处实现，由 ChatRepository 在发送流程中完成。
 */
class ConversationRepository(private val dao: ConversationDao) {

    /** 观察全部会话（按更新时间倒序）。 */
    fun observeAll(): Flow<List<ConversationEntity>> = dao.observeAll()

    /** 观察指定会话。 */
    fun observeById(id: Long): Flow<ConversationEntity?> = dao.observeById(id)

    /** 按 id 读取会话，不存在时返回 null。 */
    suspend fun getById(id: Long): ConversationEntity? = dao.getById(id)

    /**
     * 新建会话，创建时间与更新时间均取当前时刻。
     *
     * @param postHistoryPrompt 后置提示词，每轮请求中插在对话历史之后（最后一条 system）。
     * @param userName 发送消息时替换 `{{user}}` 宏所用的用户称呼。
     * @param statusBarOptimization 是否开启状态栏优化（正文与状态栏分两次请求）。
     * @param statusBodyInstruction 状态栏优化·正文请求的附加指令；空串表示使用内置默认指令。
     * @param statusBarInstruction 状态栏优化·状态栏请求的附加指令；空串表示使用内置默认指令。
     * @param statusForceNoThinking 状态栏优化·是否强制状态栏请求关闭思考模式。
     * @param storyEnhancement 剧情增强：是否开启（创建时决定，创建后不可更改）。
     * @return 新会话 id。
     */
    suspend fun create(
        title: String,
        presetId: Long? = null,
        systemPrompt: String = "",
        postHistoryPrompt: String = "",
        scanCount: Int = 20,
        recursionDepth: Int = 2,
        userName: String = "用户",
        statusBarOptimization: Boolean = false,
        statusBodyInstruction: String = "",
        statusBarInstruction: String = "",
        statusForceNoThinking: Boolean = false,
        storyEnhancement: Boolean = false,
    ): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            ConversationEntity(
                title = title,
                presetId = presetId,
                systemPrompt = systemPrompt,
                postHistoryPrompt = postHistoryPrompt,
                scanCount = scanCount,
                recursionDepth = recursionDepth,
                userName = userName,
                statusBarOptimization = statusBarOptimization,
                statusBodyInstruction = statusBodyInstruction,
                statusBarInstruction = statusBarInstruction,
                statusForceNoThinking = statusForceNoThinking,
                storyEnhancement = storyEnhancement,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /** 重命名会话。 */
    suspend fun rename(id: Long, title: String) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(title = title))
    }

    /**
     * 更新会话级设置（预设、提示词、扫描条数、递归深度、用户称呼、状态栏优化、
     * 剧情分支选择与发送轮次；剧情增强开关不在其中——创建后不可更改）。
     */
    suspend fun updateSettings(
        id: Long,
        presetId: Long?,
        systemPrompt: String,
        postHistoryPrompt: String,
        scanCount: Int,
        recursionDepth: Int,
        userName: String,
        statusBarOptimization: Boolean,
        statusBodyInstruction: String,
        statusBarInstruction: String,
        statusForceNoThinking: Boolean,
        storyBranches: Boolean,
        storyRounds: Int,
    ) {
        val existing = dao.getById(id) ?: return
        dao.update(
            existing.copy(
                presetId = presetId,
                systemPrompt = systemPrompt,
                postHistoryPrompt = postHistoryPrompt,
                scanCount = scanCount,
                recursionDepth = recursionDepth,
                userName = userName,
                statusBarOptimization = statusBarOptimization,
                statusBodyInstruction = statusBodyInstruction,
                statusBarInstruction = statusBarInstruction,
                statusForceNoThinking = statusForceNoThinking,
                storyBranches = storyBranches,
                storyRounds = storyRounds.coerceIn(1, 10),
            ),
        )
    }

    /** 仅刷新会话的更新时间（用于消息变化后把会话顶到列表前列）。 */
    suspend fun touch(id: Long) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(updatedAt = System.currentTimeMillis()))
    }

    /** 删除会话；消息与世界书条目依赖数据库外键级联删除。 */
    suspend fun delete(id: Long) {
        dao.deleteById(id)
    }
}
