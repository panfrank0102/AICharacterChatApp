package com.myagent.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myagent.app.data.db.entity.ModelPresetEntity
import com.myagent.app.data.prefs.SettingsDataStore
import com.myagent.app.data.repository.PresetRepository
import com.myagent.app.data.repository.SettingsRepository
import com.myagent.app.ui.AppGraph
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 全局设置页 ViewModel：观察全局设置与全部模型预设，并承担各项设置的写入。
 *
 * 仓库默认取自 [AppGraph]，因此界面层可直接用无参 `viewModel()` 创建；测试时可注入假实现。
 */
class GlobalSettingsViewModel(
    private val settingsRepository: SettingsRepository = AppGraph.settingsRepository,
    private val presetRepository: PresetRepository = AppGraph.presetRepository,
) : ViewModel() {

    /** 当前全局设置；初值取需求默认值，DataStore 首次发射后自动更新。 */
    val settings: StateFlow<SettingsDataStore.AppSettings> = settingsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsDataStore.AppSettings(),
        )

    /** 全部模型预设，供"默认预设"下拉选择。 */
    val presets: StateFlow<List<ModelPresetEntity>> = presetRepository.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    /** 设置默认模型预设；传 null 表示取消默认预设。 */
    fun setDefaultPresetId(v: Long?) = viewModelScope.launch {
        settingsRepository.setDefaultPresetId(v)
    }

    /** 设置全局 temperature（0~2）。 */
    fun setTemperature(v: Float) = viewModelScope.launch {
        settingsRepository.setTemperature(v)
    }

    /** 设置上下文长度上限（字符数，1000~1000000）。 */
    fun setContextLimitChars(v: Int) = viewModelScope.launch {
        settingsRepository.setContextLimitChars(v)
    }

    /** 设置全局提示词（供提示词编辑页之外的其他入口使用）。 */
    fun setGlobalPrompt(v: String) = viewModelScope.launch {
        settingsRepository.setGlobalPrompt(v)
    }
}
