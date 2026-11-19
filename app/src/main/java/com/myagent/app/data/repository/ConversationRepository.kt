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
     * @return 新会话 id。
     */
    suspend fun create(
        title: String,
        presetId: Long? = null,
        systemPrompt: String = "",
        scanCount: Int = 20,
        recursionDepth: Int = 2,
    ): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            ConversationEntity(
                title = title,
                presetId = presetId,
                systemPrompt = systemPrompt,
                scanCount = scanCount,
                recursionDepth = recursionDepth,
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

    /** 更新会话级设置（预设、提示词、扫描条数、递归深度）。 */
    suspend fun updateSettings(
        id: Long,
        presetId: Long?,
        systemPrompt: String,
        scanCount: Int,
        recursionDepth: Int,
    ) {
        val existing = dao.getById(id) ?: return
        dao.update(
            existing.copy(
                presetId = presetId,
                systemPrompt = systemPrompt,
                scanCount = scanCount,
                recursionDepth = recursionDepth,
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
