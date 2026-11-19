package com.myagent.app.ui.sessionsettings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myagent.app.data.db.entity.WorldBookEntryEntity
import com.myagent.app.data.repository.WorldbookRepository
import com.myagent.app.ui.AppGraph
import com.myagent.app.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 世界书条目编辑页的 ViewModel。
 *
 * [entryId] 小于等于 0 表示新建条目，此时 [entry] 始终为 null；
 * 编辑已有条目时在初始化阶段读取条目内容供表单回填。
 *
 * @param savedStateHandle 导航参数，必须包含会话 id，可选包含条目 id。
 * @param worldbookRepository 世界书仓库，默认使用 [AppGraph] 中的单例。
 */
class WorldbookEntryEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val worldbookRepository: WorldbookRepository = AppGraph.worldbookRepository,
) : ViewModel() {

    /** 当前会话 id，由导航参数 [Routes.ARG_CONVERSATION_ID] 传入。 */
    val conversationId: Long =
        savedStateHandle.get<Long>(Routes.ARG_CONVERSATION_ID) ?: error("缺少会话 id")

    /** 待编辑条目 id；-1 或小于等于 0 表示新建条目。 */
    val entryId: Long = savedStateHandle.get<Long>(Routes.ARG_ENTRY_ID) ?: -1L

    private val _entry = MutableStateFlow<WorldBookEntryEntity?>(null)

    /** 待编辑条目的最新内容；新建条目时为 null。 */
    val entry: StateFlow<WorldBookEntryEntity?> = _entry.asStateFlow()

    init {
        if (entryId > 0L) {
            viewModelScope.launch {
                _entry.value = worldbookRepository.getById(entryId)
            }
        }
    }

    /**
     * 保存条目，保存成功后回调 [onSaved]。
     *
     * 关键词按换行或逗号拆分并去掉空白项；优先级无法解析为整数时按 0 处理。
     * 标题会去掉首尾空白，正文保持原样。
     *
     * @param title 标题。
     * @param keywordsText 关键词原始文本，支持换行或逗号分隔。
     * @param content 正文。
     * @param priorityText 优先级的文本输入，数字越大越靠前。
     * @param enabled 是否启用。
     * @param onSaved 保存成功后的回调，通常用于返回上一页。
     */
    fun save(
        title: String,
        keywordsText: String,
        content: String,
        priorityText: String,
        enabled: Boolean,
        onSaved: () -> Unit,
    ) {
        viewModelScope.launch {
            val keywords = keywordsText.split('\n', ',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val priority = priorityText.trim().toIntOrNull() ?: 0
            worldbookRepository.save(
                id = if (entryId > 0L) entryId else 0L,
                conversationId = conversationId,
                title = title.trim(),
                keywords = keywords,
                content = content,
                priority = priority,
                enabled = enabled,
            )
            onSaved()
        }
    }
}
