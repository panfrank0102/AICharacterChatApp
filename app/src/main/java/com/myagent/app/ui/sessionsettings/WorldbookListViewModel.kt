package com.myagent.app.ui.sessionsettings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myagent.app.data.db.entity.WorldBookEntryEntity
import com.myagent.app.data.repository.WorldbookRepository
import com.myagent.app.ui.AppGraph
import com.myagent.app.ui.navigation.Routes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 世界书列表页的 ViewModel。
 *
 * 观察当前会话的全部世界书条目，并支持启用/停用切换与删除；
 * 新建与编辑条目由页面跳转到条目编辑页完成。
 *
 * @param savedStateHandle 导航参数，必须包含会话 id。
 * @param worldbookRepository 世界书仓库，默认使用 [AppGraph] 中的单例。
 */
class WorldbookListViewModel(
    savedStateHandle: SavedStateHandle,
    private val worldbookRepository: WorldbookRepository = AppGraph.worldbookRepository,
) : ViewModel() {

    /** 当前会话 id，由导航参数 [Routes.ARG_CONVERSATION_ID] 传入。 */
    val conversationId: Long =
        savedStateHandle.get<Long>(Routes.ARG_CONVERSATION_ID) ?: error("缺少会话 id")

    /** 当前会话的世界书条目，已由仓库按优先级降序、创建顺序升序排序。 */
    val entries: StateFlow<List<WorldBookEntryEntity>> =
        worldbookRepository.observeByConversation(conversationId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    /**
     * 切换指定条目的启用状态（启用变停用、停用变启用）。
     *
     * @param entry 列表中被点击的条目快照。
     */
    fun toggleEnabled(entry: WorldBookEntryEntity) {
        viewModelScope.launch {
            worldbookRepository.save(
                id = entry.id,
                conversationId = entry.conversationId,
                title = entry.title,
                keywords = splitKeywords(entry.keywords),
                content = entry.content,
                priority = entry.priority,
                enabled = !entry.enabled,
            )
        }
    }

    /**
     * 删除指定世界书条目。
     *
     * @param id 条目 id。
     */
    fun delete(id: Long) {
        viewModelScope.launch {
            worldbookRepository.delete(id)
        }
    }

    /** 把数据库中按换行/逗号分隔的关键词字符串还原为列表。 */
    private fun splitKeywords(raw: String): List<String> =
        raw.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }
}
