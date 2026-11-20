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
 * 提示词编辑页 ViewModel：编辑全局提示词、会话前置提示词或会话后置提示词。
 *
 * 路由参数 `conversationId` 为空或小于等于 0 时表示编辑全局提示词；
 * 否则由 `promptKind` 区分前置（默认）与后置提示词。
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

    /** 提示词类型：前置（默认）或后置；仅对会话提示词生效。 */
    val promptKind: String = savedStateHandle.get<String>(Routes.ARG_PROMPT_KIND)
        ?: Routes.PROMPT_KIND_SESSION

    /** 当前是否在编辑会话后置提示词。 */
    val isPostHistory: Boolean
        get() = conversationId != null && promptKind == Routes.PROMPT_KIND_POST_HISTORY

    /** 编辑中的提示词文本。 */
    val text = MutableStateFlow("")

    init {
        viewModelScope.launch {
            val targetId = conversationId
            text.value = when {
                targetId == null -> settingsRepository.current().globalPrompt
                isPostHistory -> conversationRepository.getById(targetId)?.postHistoryPrompt ?: ""
                else -> conversationRepository.getById(targetId)?.systemPrompt ?: ""
            }
        }
    }

    /**
     * 保存提示词：全局提示词写入 DataStore；会话提示词按类型写回会话记录（保留其余设置不变）。
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
                    systemPrompt = if (isPostHistory) conversation.systemPrompt else text.value,
                    postHistoryPrompt = if (isPostHistory) text.value else conversation.postHistoryPrompt,
                    scanCount = conversation.scanCount,
                    recursionDepth = conversation.recursionDepth,
                    userName = conversation.userName,
                )
            }
            onSaved()
        }
    }
}
