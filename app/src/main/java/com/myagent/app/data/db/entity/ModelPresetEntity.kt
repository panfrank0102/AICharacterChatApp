package com.myagent.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 模型预设：一组 OpenAI 兼容接口的访问配置。
 *
 * [apiKeyEncrypted] 仅保存经 Android Keystore 加密后的密文，
 * 明文解密请使用 [com.myagent.app.data.prefs.ApiKeyCrypto]。
 */
@Entity(tableName = "model_preset")
data class ModelPresetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val baseUrl: String,
    val apiKeyEncrypted: String,
    val model: String,
)
