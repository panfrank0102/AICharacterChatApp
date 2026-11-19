package com.myagent.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 全局设置的数据存储文件（settings.preferences_pb）。 */
private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * 全局设置（DataStore Preferences）。
 *
 * 需在 Application 中调用 [init] 完成初始化，之后通过 [settingsFlow] 观察、通过 set 系列方法修改。
 */
object SettingsDataStore {

    private lateinit var appContext: Context

    private val globalPromptKey = stringPreferencesKey("global_prompt")
    private val contextLimitCharsKey = intPreferencesKey("context_limit_chars")
    private val temperatureKey = floatPreferencesKey("temperature")
    private val defaultPresetIdKey = longPreferencesKey("default_preset_id")

    /** 全局设置的快照。 */
    data class AppSettings(
        val globalPrompt: String = "",
        val contextLimitChars: Int = 100_000,
        val temperature: Float = 0.7f,
        val defaultPresetId: Long? = null,
    )

    /** 使用 Application Context 完成初始化（幂等）。 */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** 全局设置的数据流；未设置的键返回默认值。 */
    val settingsFlow: Flow<AppSettings>
        get() = appContext.dataStore.data.map { prefs ->
            AppSettings(
                globalPrompt = prefs[globalPromptKey] ?: "",
                contextLimitChars = prefs[contextLimitCharsKey] ?: 100_000,
                temperature = prefs[temperatureKey] ?: 0.7f,
                defaultPresetId = prefs[defaultPresetIdKey]?.takeIf { it != -1L },
            )
        }

    suspend fun setGlobalPrompt(value: String) {
        appContext.dataStore.edit { prefs ->
            prefs[globalPromptKey] = value
        }
    }

    suspend fun setContextLimitChars(value: Int) {
        appContext.dataStore.edit { prefs ->
            prefs[contextLimitCharsKey] = value
        }
    }

    suspend fun setTemperature(value: Float) {
        appContext.dataStore.edit { prefs ->
            prefs[temperatureKey] = value
        }
    }

    suspend fun setDefaultPresetId(value: Long?) {
        appContext.dataStore.edit { prefs ->
            prefs[defaultPresetIdKey] = value ?: -1L
        }
    }
}
