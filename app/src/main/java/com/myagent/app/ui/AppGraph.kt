package com.myagent.app.ui

import com.myagent.app.data.db.AppDatabase
import com.myagent.app.data.prefs.ApiKeyCrypto
import com.myagent.app.data.repository.ChatRepository
import com.myagent.app.data.repository.ConversationRepository
import com.myagent.app.data.repository.DataStoreSettingsRepository
import com.myagent.app.data.repository.MessageRepository
import com.myagent.app.data.repository.PresetRepository
import com.myagent.app.data.repository.SettingsRepository
import com.myagent.app.data.repository.WorldbookRepository
import com.myagent.app.network.ChatApi
import com.myagent.app.network.OpenAiClient

/**
 * 手工依赖图：集中创建全局单例仓库与网络客户端（不引入第三方 DI 框架）。
 *
 * 所有 ViewModel 通过构造函数默认参数引用这里的单例，测试时可注入假实现。
 */
object AppGraph {

    private val db
        get() = AppDatabase.instance

    /** 全局设置仓库。 */
    val settingsRepository: SettingsRepository by lazy { DataStoreSettingsRepository() }

    /** 模型预设仓库。 */
    val presetRepository: PresetRepository by lazy { PresetRepository(db.presetDao()) }

    /** 会话仓库。 */
    val conversationRepository: ConversationRepository by lazy { ConversationRepository(db.conversationDao()) }

    /** 消息仓库。 */
    val messageRepository: MessageRepository by lazy { MessageRepository(db.messageDao()) }

    /** 世界书仓库。 */
    val worldbookRepository: WorldbookRepository by lazy { WorldbookRepository(db.worldBookDao()) }

    /** 聊天接口实现（OpenAI 兼容 + SSE 流式）。 */
    val chatApi: ChatApi by lazy { OpenAiClient() }

    /** 聊天编排仓库。 */
    val chatRepository: ChatRepository by lazy {
        ChatRepository(
            conversationDao = db.conversationDao(),
            messageDao = db.messageDao(),
            worldBookDao = db.worldBookDao(),
            presetDao = db.presetDao(),
            settings = settingsRepository,
            api = chatApi,
            keyDecryptor = ApiKeyCrypto::decrypt,
        )
    }
}
