package com.myagent.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myagent.app.data.db.entity.ModelPresetEntity
import com.myagent.app.data.repository.PresetRepository
import com.myagent.app.ui.AppGraph
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 模型预设列表页 ViewModel：观察全部预设并承担删除操作。
 *
 * 仓库默认取自 [AppGraph]，界面层可直接用无参 `viewModel()` 创建；测试时可注入假实现。
 */
class PresetListViewModel(
    private val presetRepository: PresetRepository = AppGraph.presetRepository,
) : ViewModel() {

    /** 全部模型预设（仓库已按名称排序）。 */
    val presets: StateFlow<List<ModelPresetEntity>> = presetRepository.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    /** 删除指定预设；被删除的预设若被会话引用，由会话侧自行处理。 */
    fun delete(id: Long) = viewModelScope.launch {
        presetRepository.delete(id)
    }
}
