package com.myagent.app.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myagent.app.data.repository.ConversationRepository
import com.myagent.app.data.repository.SettingsRepository
import com.myagent.app.domain.StatusBarInstructions
import com.myagent.app.ui.AppGraph
import com.myagent.app.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 提示词编辑页 ViewModel：编辑全局提示词、会话前置/后置提示词，
 * 以及状态栏优化功能的两条临时指令（正文指令、状态栏指令）。
 *
 * 路由参数 `conversationId` 为空或小于等于 0 时表示编辑全局提示词；
 * 否则由 `promptKind` 区分编辑对象：
 * - `session`（默认）：会话前置提示词；
 * - `postHistory`：会话后置提示词；
 * - `statusBody`：状态栏优化·正文请求附加指令；
 * - `statusBar`：状态栏优化·状态栏请求附加指令。
 *
 * 状态栏指令自定义内容为空串时表示使用内置默认指令；编辑页加载时会把默认指令
 * 预填进文本框，保存空白即恢复为使用默认。
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

    /** 提示词类型：前置（默认）/后置/状态栏正文指令/状态栏指令；仅对会话设置生效。 */
    val promptKind: String = savedStateHandle.get<String>(Routes.ARG_PROMPT_KIND)
        ?: Routes.PROMPT_KIND_SESSION

    /** 当前是否在编辑会话后置提示词。 */
    val isPostHistory: Boolean
        get() = conversationId != null && promptKind == Routes.PROMPT_KIND_POST_HISTORY

    /** 当前是否在编辑状态栏优化·正文指令。 */
    val isStatusBody: Boolean
        get() = conversationId != null && promptKind == Routes.PROMPT_KIND_STATUS_BODY

    /** 当前是否在编辑状态栏优化·状态栏指令。 */
    val isStatusBar: Boolean
        get() = conversationId != null && promptKind == Routes.PROMPT_KIND_STATUS_BAR

    /** 编辑中的提示词文本。 */
    val text = MutableStateFlow("")

    init {
        viewModelScope.launch {
            val targetId = conversationId
            text.value = when {
                targetId == null -> settingsRepository.current().globalPrompt
                isPostHistory -> conversationRepository.getById(targetId)?.postHistoryPrompt ?: ""
                isStatusBody -> StatusBarInstructions.resolveBodyInstruction(
                    conversationRepository.getById(targetId)?.statusBodyInstruction.orEmpty(),
                )

                isStatusBar -> StatusBarInstructions.resolveStatusInstruction(
                    conversationRepository.getById(targetId)?.statusBarInstruction.orEmpty(),
                )

                else -> conversationRepository.getById(targetId)?.systemPrompt ?: ""
            }
        }
    }

    /**
     * 保存提示词：全局提示词写入 DataStore；会话提示词按类型写回会话记录（保留其余设置不变）。
     *
     * 状态栏指令保存时把文本 trim 后落库；与内置默认指令完全一致时同样落库（行为等价），
     * 清空则恢复为使用默认指令。
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
                val saved = text.value.trim()
                conversationRepository.updateSettings(
                    id = conversation.id,
                    presetId = conversation.presetId,
                    systemPrompt = when {
                        isStatusBody || isStatusBar -> conversation.systemPrompt
                        isPostHistory -> conversation.systemPrompt
                        else -> saved
                    },
                    postHistoryPrompt = when {
                        isStatusBody || isStatusBar -> conversation.postHistoryPrompt
                        isPostHistory -> saved
                        else -> conversation.postHistoryPrompt
                    },
                    scanCount = conversation.scanCount,
                    recursionDepth = conversation.recursionDepth,
                    userName = conversation.userName,
                    statusBarOptimization = conversation.statusBarOptimization,
                    statusBodyInstruction = when {
                        isStatusBody -> saved
                        else -> conversation.statusBodyInstruction
                    },
                    statusBarInstruction = when {
                        isStatusBar -> saved
                        else -> conversation.statusBarInstruction
                    },
                    statusForceNoThinking = conversation.statusForceNoThinking,
                    storyBranches = conversation.storyBranches,
                    storyRounds = conversation.storyRounds,
                )
            }
            onSaved()
        }
    }
}
