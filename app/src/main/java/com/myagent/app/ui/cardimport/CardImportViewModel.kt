package com.myagent.app.ui.cardimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myagent.app.data.repository.CharacterCardImporter
import com.myagent.app.domain.import.CharacterCard
import com.myagent.app.ui.AppGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 角色卡导入预览页的 ViewModel。
 *
 * 读取 [ImportState] 中暂存的角色卡，并对外提供提示词预览、世界书统计与导入执行能力。
 * 仓库默认取自 [AppGraph]，因此界面层可直接用无参 `viewModel()` 创建，测试时可注入假实现。
 *
 * @param importer 角色卡导入器，默认使用 [AppGraph] 中的单例
 */
class CardImportViewModel(
    private val importer: CharacterCardImporter = AppGraph.characterCardImporter,
) : ViewModel() {

    /** 待导入的角色卡；没有暂存卡片时为 null。 */
    val card: StateFlow<CharacterCard?> = MutableStateFlow(ImportState.pendingCard)

    /** 导入后将写入会话的系统提示词预览。 */
    val promptPreview: String
        get() = card.value?.let { currentCard -> CharacterCardImporter.buildSystemPrompt(currentCard) }.orEmpty()

    /** 世界书中没有关键词的条目数（这些条目也会导入，补充关键词后才会触发）。 */
    val keywordlessWorldbookCount: Int
        get() = card.value?.worldBook?.entries?.count { entry ->
            CharacterCardImporter.splitKeywords(entry.keys, entry.secondaryKeys).isEmpty()
        } ?: 0

    /** 世界书条目总数。 */
    val totalWorldbookCount: Int
        get() = card.value?.worldBook?.entries?.size ?: 0

    /** 是否正在执行导入。 */
    val importing = MutableStateFlow(false)

    /** 最近一次导入失败的错误信息；null 表示当前没有错误。 */
    val error = MutableStateFlow<String?>(null)

    /**
     * 执行导入。
     *
     * 导入成功后清空 [ImportState] 中的暂存卡片，并把新会话 id 交给 onDone 回调；
     * 失败时把错误信息写入 [error] 供界面用 Snackbar 展示。
     *
     * @param selectedGreetingIndexes 选中的备用开场白下标
     * @param customOpening 自定义开场白内容，空白表示不插入
     * @param storyEnhancement 是否为新会话开启剧情增强（创建后不可更改）
     * @param onDone 导入成功的回调，参数为新建会话 id
     */
    fun import(
        selectedGreetingIndexes: Set<Int>,
        customOpening: String,
        storyEnhancement: Boolean,
        onDone: (Long) -> Unit,
    ) {
        viewModelScope.launch {
            importing.value = true
            error.value = null
            try {
                val currentCard = card.value ?: return@launch
                val result = importer.import(
                    CharacterCardImporter.Options(
                        card = currentCard,
                        selectedGreetingIndexes = selectedGreetingIndexes,
                        customOpening = customOpening,
                        storyEnhancement = storyEnhancement,
                    ),
                )
                ImportState.pendingCard = null
                onDone(result.conversationId)
            } catch (e: Exception) {
                error.value = e.message ?: "导入失败"
            } finally {
                importing.value = false
            }
        }
    }

    /** 清除当前错误信息（Snackbar 展示后调用）。 */
    fun clearError() {
        error.value = null
    }
}
