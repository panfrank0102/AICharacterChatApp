package com.myagent.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 模型预设：一组 OpenAI 兼容接口的访问配置。
 *
 * [apiKeyEncrypted] 仅保存经 Android Keystore 加密后的密文，
 * 明文解密请使用 [com.myagent.app.data.prefs.ApiKeyCrypto]。
 *
 * [reasoningEffort] 为思考强度（DeepSeek `reasoning_effort` 参数）：
 * `none` 不思考、`low` 低、`medium` 中、`high` 高；null 表示不设置
 * （请求中不携带该参数，以兼容不支持思考强度的其他兼容模型）。
 */
@Entity(tableName = "model_preset")
data class ModelPresetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val baseUrl: String,
    val apiKeyEncrypted: String,
    val model: String,
    @ColumnInfo(name = "reasoning_effort")
    val reasoningEffort: String? = null,
)
