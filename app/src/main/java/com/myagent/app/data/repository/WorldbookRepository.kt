package com.myagent.app.data.repository

import com.myagent.app.data.db.dao.WorldBookDao
import com.myagent.app.data.db.entity.WorldBookEntryEntity
import com.myagent.app.domain.WorldbookMatcher
import kotlinx.coroutines.flow.Flow

/**
 * 世界书仓库：负责会话世界书条目的维护，并提供转换为领域匹配条目的能力。
 *
 * keywords 在数据库中按换行符分隔存储，读写时负责与 List 之间的转换。
 */
class WorldbookRepository(private val dao: WorldBookDao) {

    /** 观察指定会话的世界书条目（按优先级降序、创建顺序升序）。 */
    fun observeByConversation(id: Long): Flow<List<WorldBookEntryEntity>> = dao.observeByConversation(id)

    /** 读取指定会话中所有已启用的世界书条目。 */
    suspend fun getEnabledByConversation(id: Long): List<WorldBookEntryEntity> = dao.getEnabledByConversation(id)

    /** 按 id 读取世界书条目，不存在时返回 null。 */
    suspend fun getById(id: Long): WorldBookEntryEntity? = dao.getById(id)

    /**
     * 新增或更新世界书条目。
     *
     * @param id 条目 id，0 表示新增。
     * @param keywords 关键词列表，落库前用换行符连接。
     */
    suspend fun save(
        id: Long = 0,
        conversationId: Long,
        title: String,
        keywords: List<String>,
        content: String,
        priority: Int = 0,
        enabled: Boolean = true,
    ) {
        val joinedKeywords = keywords.joinToString("\n")
        val existing = if (id == 0L) null else dao.getById(id)
        if (existing != null) {
            dao.update(
                existing.copy(
                    conversationId = conversationId,
                    title = title,
                    keywords = joinedKeywords,
                    content = content,
                    priority = priority,
                    enabled = enabled,
                ),
            )
        } else {
            dao.insert(
                WorldBookEntryEntity(
                    id = id,
                    conversationId = conversationId,
                    title = title,
                    keywords = joinedKeywords,
                    content = content,
                    priority = priority,
                    enabled = enabled,
                ),
            )
        }
    }

    /** 按 id 删除世界书条目，不存在时静默忽略。 */
    suspend fun delete(id: Long) {
        dao.getById(id)?.let { dao.delete(it) }
    }

    /** 把当前会话已启用的条目转换为领域层匹配所需的 [WorldbookMatcher.Entry] 列表。 */
    suspend fun toMatcherEntries(conversationId: Long): List<WorldbookMatcher.Entry> =
        getEnabledByConversation(conversationId).map { entry ->
            WorldbookMatcher.Entry(
                entry.id,
                entry.keywords.split('\n', ',').map { keyword -> keyword.trim() }.filter { keyword -> keyword.isNotEmpty() },
                entry.content,
                entry.priority,
            )
        }
}
