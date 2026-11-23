package com.myagent.app.ui.sessionsettings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myagent.app.data.db.entity.ConversationEntity
import com.myagent.app.data.db.entity.ModelPresetEntity
import com.myagent.app.data.repository.ConversationRepository
import com.myagent.app.data.repository.PresetRepository
import com.myagent.app.ui.AppGraph
import com.myagent.app.ui.navigation.Routes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 会话设置页的 ViewModel。
 *
 * 负责观察当前会话与全部模型预设，并把预设、世界书扫描条数、世界书递归深度的修改写回会话。
 * 会话提示词与世界书条目通过页面跳转进入各自的页面维护。
 *
 * @param savedStateHandle 导航参数，必须包含会话 id。
 * @param conversationRepository 会话仓库，默认使用 [AppGraph] 中的单例。
 * @param presetRepository 模型预设仓库，默认使用 [AppGraph] 中的单例。
 */
class SessionSettingsViewModel(
    savedStateHandle: SavedStateHandle,
    private val conversationRepository: ConversationRepository = AppGraph.conversationRepository,
    private val presetRepository: PresetRepository = AppGraph.presetRepository,
) : ViewModel() {

    /** 当前会话 id，由导航参数 [Routes.ARG_CONVERSATION_ID] 传入。 */
    val conversationId: Long =
        savedStateHandle.get<Long>(Routes.ARG_CONVERSATION_ID) ?: error("缺少会话 id")

    /** 当前会话快照；会话尚未加载或已被删除时为 null。 */
    val conversation: StateFlow<ConversationEntity?> =
        conversationRepository.observeById(conversationId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    /** 全部模型预设，供预设下拉选择使用。 */
    val presets: StateFlow<List<ModelPresetEntity>> =
        presetRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    /**
     * 切换当前会话使用的模型预设。
     *
     * @param presetId 预设 id；传 null 表示继承全局默认预设。
     */
    fun setPreset(presetId: Long?) {
        updateSettings { it.copy(presetId = presetId) }
    }

    /**
     * 设置世界书扫描条数，超出 0..200 时自动收敛到边界。
     *
     * @param v 新的扫描条数，0 表示关闭世界书扫描。
     */
    fun setScanCount(v: Int) {
        val clamped = v.coerceIn(MIN_SCAN_COUNT, MAX_SCAN_COUNT)
        updateSettings { it.copy(scanCount = clamped) }
    }

    /**
     * 设置世界书递归深度，超出 0..5 时自动收敛到边界。
     *
     * @param v 新的递归深度，0 表示不递归。
     */
    fun setRecursionDepth(v: Int) {
        val clamped = v.coerceIn(MIN_RECURSION_DEPTH, MAX_RECURSION_DEPTH)
        updateSettings { it.copy(recursionDepth = clamped) }
    }

    /**
     * 设置发送消息时替换 `{{user}}` 宏所用的用户称呼。
     *
     * @param v 新的用户称呼，空白时回落到默认称呼"用户"。
     */
    fun setUserName(v: String) {
        val name = v.trim().ifBlank { DEFAULT_USER_NAME }
        updateSettings { it.copy(userName = name) }
    }

    /**
     * 开关「状态栏优化」（正文与状态栏分两次请求分别流式生成）。
     *
     * @param v true 表示开启；关闭时子设置（指令与强制不思考）保留原值，便于再次开启。
     */
    fun setStatusBarOptimization(v: Boolean) {
        updateSettings { it.copy(statusBarOptimization = v) }
    }

    /**
     * 开关「状态栏请求强制不思考」。
     *
     * @param v true 表示状态栏请求在预设已设置思考强度时强制使用"不思考"；
     * 预设本身未设置思考强度时该开关不生效。
     */
    fun setStatusForceNoThinking(v: Boolean) {
        updateSettings { it.copy(statusForceNoThinking = v) }
    }

    /**
     * 开关「剧情分支选择」（剧情增强会话）：总结请求同时返回四个备选选项。
     */
    fun setStoryBranches(v: Boolean) {
        updateSettings { it.copy(storyBranches = v) }
    }

    /**
     * 设置剧情增强的发送轮次（1~10）：每轮请求发送最近 N 轮原话，更早的历史用剧情总结。
     */
    fun setStoryRounds(v: Int) {
        updateSettings { it.copy(storyRounds = v.coerceIn(MIN_STORY_ROUNDS, MAX_STORY_ROUNDS)) }
    }

    /** 先读取数据库中的最新会话，再只覆盖指定字段后写回，避免覆盖其他并发修改。 */
    private fun updateSettings(transform: (ConversationEntity) -> ConversationEntity) {
        viewModelScope.launch {
            val existing = conversationRepository.getById(conversationId) ?: return@launch
            val updated = transform(existing)
            conversationRepository.updateSettings(
                id = conversationId,
                presetId = updated.presetId,
                systemPrompt = updated.systemPrompt,
                postHistoryPrompt = updated.postHistoryPrompt,
                scanCount = updated.scanCount,
                recursionDepth = updated.recursionDepth,
                userName = updated.userName,
                statusBarOptimization = updated.statusBarOptimization,
                statusBodyInstruction = updated.statusBodyInstruction,
                statusBarInstruction = updated.statusBarInstruction,
                statusForceNoThinking = updated.statusForceNoThinking,
                storyBranches = updated.storyBranches,
                storyRounds = updated.storyRounds,
            )
        }
    }

    private companion object {

        /** 用户称呼为空时的默认值，与数据库默认值一致。 */
        const val DEFAULT_USER_NAME = "用户"

        /** 剧情增强发送轮次下限。 */
        const val MIN_STORY_ROUNDS = 1

        /** 剧情增强发送轮次上限。 */
        const val MAX_STORY_ROUNDS = 10

        /** 扫描条数下限。 */
        const val MIN_SCAN_COUNT = 0

        /** 扫描条数上限。 */
        const val MAX_SCAN_COUNT = 200

        /** 递归深度下限。 */
        const val MIN_RECURSION_DEPTH = 0

        /** 递归深度上限。 */
        const val MAX_RECURSION_DEPTH = 5
    }
}
