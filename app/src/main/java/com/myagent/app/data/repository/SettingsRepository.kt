package com.myagent.app.data.repository

import com.myagent.app.data.prefs.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * 全局设置仓库：统一暴露全局提示词、上下文长度上限、temperature 与默认预设的读写能力。
 *
 * 界面层只依赖本接口，生产环境使用 [DataStoreSettingsRepository]，单元测试可注入内存假实现。
 */
interface SettingsRepository {

    /** 全局设置的数据流，便于界面持续观察设置变化。 */
    val settingsFlow: Flow<SettingsDataStore.AppSettings>

    /** 读取当前全局设置快照。 */
    suspend fun current(): SettingsDataStore.AppSettings

    /** 更新全局提示词。 */
    suspend fun setGlobalPrompt(v: String)

    /** 更新上下文长度上限（字符数）。 */
    suspend fun setContextLimitChars(v: Int)

    /** 更新全局 temperature。 */
    suspend fun setTemperature(v: Float)

    /** 更新默认模型预设；传 null 表示取消默认预设。 */
    suspend fun setDefaultPresetId(v: Long?)
}

/**
 * 基于 [SettingsDataStore] 的全局设置仓库实现。
 *
 * 观察类方法直接复用 DataStore 的数据流；写入类方法全部委托给 DataStore。
 */
class DataStoreSettingsRepository : SettingsRepository {

    override val settingsFlow: Flow<SettingsDataStore.AppSettings>
        get() = SettingsDataStore.settingsFlow

    override suspend fun current(): SettingsDataStore.AppSettings = SettingsDataStore.settingsFlow.first()

    override suspend fun setGlobalPrompt(v: String) {
        SettingsDataStore.setGlobalPrompt(v)
    }

    override suspend fun setContextLimitChars(v: Int) {
        SettingsDataStore.setContextLimitChars(v)
    }

    override suspend fun setTemperature(v: Float) {
        SettingsDataStore.setTemperature(v)
    }

    override suspend fun setDefaultPresetId(v: Long?) {
        SettingsDataStore.setDefaultPresetId(v)
    }
}
