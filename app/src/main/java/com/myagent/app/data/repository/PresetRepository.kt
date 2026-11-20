package com.myagent.app.data.repository

import com.myagent.app.data.db.dao.PresetDao
import com.myagent.app.data.db.entity.ModelPresetEntity
import com.myagent.app.data.prefs.ApiKeyCrypto
import kotlinx.coroutines.flow.Flow

/**
 * 模型预设仓库：负责预设的增删改查，并在写入时对 API Key 做加密。
 *
 * API Key 只以 [ModelPresetEntity.apiKeyEncrypted] 密文形式落库；
 * 仅 [save] 与 [decryptKey] 会接触加解密逻辑。
 */
class PresetRepository(private val dao: PresetDao) {

    /** 观察全部预设（按名称排序）。 */
    fun observeAll(): Flow<List<ModelPresetEntity>> = dao.observeAll()

    /** 一次性读取全部预设。 */
    suspend fun getAll(): List<ModelPresetEntity> = dao.getAll()

    /** 按 id 读取预设，不存在时返回 null。 */
    suspend fun getById(id: Long): ModelPresetEntity? = dao.getById(id)

    /**
     * 新增或更新预设。
     *
     * @param id 预设 id，0 表示新增。
     * @param apiKeyPlain API Key 明文；为空时编辑已有预设会保留原密文，新增预设则保存空串。
     * @param reasoningEffort 思考强度（`none`/`low`/`medium`/`high`）；null 表示不设置（不发送该参数）。
     */
    suspend fun save(
        id: Long = 0,
        name: String,
        baseUrl: String,
        apiKeyPlain: String,
        model: String,
        reasoningEffort: String? = null,
    ) {
        val existing = if (id == 0L) null else dao.getById(id)
        val encrypted = when {
            apiKeyPlain.isNotBlank() -> ApiKeyCrypto.encrypt(apiKeyPlain)
            existing != null -> existing.apiKeyEncrypted
            else -> ""
        }
        val entity = if (existing != null) {
            existing.copy(
                name = name,
                baseUrl = baseUrl,
                apiKeyEncrypted = encrypted,
                model = model,
                reasoningEffort = reasoningEffort,
            )
        } else {
            ModelPresetEntity(
                id = id,
                name = name,
                baseUrl = baseUrl,
                apiKeyEncrypted = encrypted,
                model = model,
                reasoningEffort = reasoningEffort,
            )
        }
        dao.upsert(entity)
    }

    /** 读取并解密指定预设的 API Key；预设不存在或解密失败时返回 null。 */
    suspend fun decryptKey(id: Long): String? = dao.getById(id)?.let { ApiKeyCrypto.decrypt(it.apiKeyEncrypted) }

    /** 按 id 删除预设，不存在时静默忽略。 */
    suspend fun delete(id: Long) {
        dao.getById(id)?.let { dao.delete(it) }
    }

    /** 删除指定预设实体。 */
    suspend fun delete(entity: ModelPresetEntity) {
        dao.delete(entity)
    }
}
