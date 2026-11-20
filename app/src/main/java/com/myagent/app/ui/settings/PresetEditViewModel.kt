package com.myagent.app.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myagent.app.data.db.entity.ModelPresetEntity
import com.myagent.app.data.repository.PresetRepository
import com.myagent.app.ui.AppGraph
import com.myagent.app.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 模型预设编辑页 ViewModel：按路由参数加载已有预设，并负责保存。
 *
 * 路由参数 `presetId` 为 -1 时表示新建，其余值表示编辑对应预设。
 *
 * @param savedStateHandle 由导航框架注入，用于读取路由参数。
 * @param presetRepository 预设仓库，默认取自 [AppGraph]。
 */
class PresetEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val presetRepository: PresetRepository = AppGraph.presetRepository,
) : ViewModel() {

    /** 预设 id；-1 表示新建。 */
    val presetId: Long = savedStateHandle.get<Long>(Routes.ARG_PRESET_ID) ?: -1L

    private val _preset = MutableStateFlow<ModelPresetEntity?>(null)

    /** 编辑中的预设；新建或尚未加载完成时为 null。 */
    val preset: StateFlow<ModelPresetEntity?> = _preset

    init {
        if (presetId > 0) {
            viewModelScope.launch {
                _preset.value = presetRepository.getById(presetId)
            }
        }
    }

    /**
     * 保存预设。
     *
     * [apiKeyPlain] 留空时仓库会自动保留已有预设的旧密文，因此编辑时无需回填明文。
     * [reasoningEffort] 为思考强度（`none`/`low`/`medium`/`high`），null 表示不设置（不发送该参数）。
     *
     * @param onSaved 保存成功后的回调（通常用于返回上一页）。
     */
    fun save(
        name: String,
        baseUrl: String,
        apiKeyPlain: String,
        model: String,
        reasoningEffort: String?,
        onSaved: () -> Unit,
    ) {
        viewModelScope.launch {
            presetRepository.save(
                id = if (presetId > 0) presetId else 0,
                name = name.trim(),
                baseUrl = baseUrl.trim(),
                apiKeyPlain = apiKeyPlain,
                model = model.trim(),
                reasoningEffort = reasoningEffort,
            )
            onSaved()
        }
    }
}
