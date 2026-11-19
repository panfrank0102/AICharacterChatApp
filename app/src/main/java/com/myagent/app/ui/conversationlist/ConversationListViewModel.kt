package com.myagent.app.ui.conversationlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myagent.app.data.db.entity.ConversationEntity
import com.myagent.app.data.repository.ConversationRepository
import com.myagent.app.ui.AppGraph
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 会话列表页 ViewModel：暴露全部会话，并承担新建、重命名与删除操作。
 *
 * 仓库默认取自 [AppGraph]，因此界面层可直接用无参 `viewModel()` 创建，测试时可注入假实现。
 */
class ConversationListViewModel(
    private val conversationRepository: ConversationRepository = AppGraph.conversationRepository,
) : ViewModel() {

    /** 全部会话（仓库已按更新时间倒序输出）。 */
    val conversations: StateFlow<List<ConversationEntity>> = conversationRepository
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 新建一个空标题会话，返回新会话 id（标题由后续首条用户消息自动生成）。 */
    suspend fun createConversation(): Long = conversationRepository.create(title = "")

    /** 重命名指定会话。 */
    fun rename(id: Long, title: String) = viewModelScope.launch {
        conversationRepository.rename(id, title)
    }

    /** 删除指定会话（消息与世界书条目由数据库外键级联删除）。 */
    fun delete(id: Long) = viewModelScope.launch {
        conversationRepository.delete(id)
    }
}
