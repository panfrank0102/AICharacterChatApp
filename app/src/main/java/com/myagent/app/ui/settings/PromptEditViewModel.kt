package com.myagent.app.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myagent.app.data.repository.ConversationRepository
import com.myagent.app.data.repository.SettingsRepository
import com.myagent.app.ui.AppGraph
import com.myagent.app.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 提示词编辑页 ViewModel：编辑全局提示词或指定会话的提示词。
 *
 * 路由参数 `conversationId` 为空或小于等于 0 时表示编辑全局提示词。
 *
 * @param savedStateHandle 由导航框架注入，用于读取路由参数。
 * @param settingsRepository 全局设置仓库，默认取自 [AppGraph]。
 * @param conversationRepository 会话仓库，默认取自 [AppGraph]。
 */
class PromptEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository = AppGraph.settingsRepository,
    private val conversationRepository: ConversationRepository = AppGraph.conversationRepository,
) : ViewModel() {

    /** 目标会话 id；null 表示编辑全局提示词。 */
    val conversationId: Long? = savedStateHandle
        .get<Long>(Routes.ARG_CONVERSATION_ID)
        ?.takeIf { it > 0 }

    /** 编辑中的提示词文本。 */
    val text = MutableStateFlow("")

    init {
        viewModelScope.launch {
            val targetId = conversationId
            text.value = if (targetId == null) {
                settingsRepository.current().globalPrompt
            } else {
                conversationRepository.getById(targetId)?.systemPrompt ?: ""
            }
        }
    }

    /**
     * 保存提示词：全局提示词写入 DataStore，会话提示词写回会话记录（保留其余设置不变）。
     *
     * @param onSaved 保存成功后的回调（通常用于返回上一页）。
     */
    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val targetId = conversationId
            if (targetId == null) {
                settingsRepository.setGlobalPrompt(text.value)
            } else {
                val conversation = conversationRepository.getById(targetId) ?: return@launch
                conversationRepository.updateSettings(
                    id = conversation.id,
                    presetId = conversation.presetId,
                    systemPrompt = text.value,
                    scanCount = conversation.scanCount,
                    recursionDepth = conversation.recursionDepth,
                )
            }
            onSaved()
        }
    }
}
