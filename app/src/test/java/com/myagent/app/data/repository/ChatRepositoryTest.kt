package com.myagent.app.data.repository

import com.myagent.app.data.db.dao.ConversationDao
import com.myagent.app.data.db.dao.MessageDao
import com.myagent.app.data.db.dao.PresetDao
import com.myagent.app.data.db.dao.WorldBookDao
import com.myagent.app.data.db.entity.ConversationEntity
import com.myagent.app.data.db.entity.MessageEntity
import com.myagent.app.data.db.entity.ModelPresetEntity
import com.myagent.app.data.db.entity.WorldBookEntryEntity
import com.myagent.app.data.prefs.SettingsDataStore
import com.myagent.app.network.ApiException
import com.myagent.app.network.ChatApi
import com.myagent.app.network.Kind
import com.myagent.app.network.dto.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ChatRepository] 的纯 JVM 单元测试。
 *
 * 全部依赖（4 个 DAO、[ChatApi]、[SettingsRepository]）均使用文件内定义的内存假实现，
 * 不依赖 Room、Android SDK 或真实网络。
 */
class ChatRepositoryTest {

    private val conversationDao = FakeConversationDao()
    private val messageDao = FakeMessageDao()
    private val worldBookDao = FakeWorldBookDao()
    private val presetDao = FakePresetDao()

    @Test
    fun sendMessage_persistsMessagesAndAssemblesRequestInOrder() = runBlocking {
        seedConversation(systemPrompt = "会话人设")
        seedPreset()
        messageDao.seed(conversationId = CONVERSATION_ID, role = "user", content = "旧问题")
        messageDao.seed(conversationId = CONVERSATION_ID, role = "assistant", content = "旧回答")
        val api = FakeChatApi(chunks = listOf("你", "好"))

        val deltas = repository(
            api = api,
            settings = SettingsDataStore.AppSettings(globalPrompt = "全局人设"),
        ).sendMessage(CONVERSATION_ID, "你好呀").toList()

        assertEquals(listOf("你", "好"), deltas)

        val stored = messageDao.all(CONVERSATION_ID)
        assertEquals(4, stored.size)
        assertEquals(listOf("旧问题", "旧回答", "你好呀", "你好"), stored.map { it.content })
        assertEquals("user", stored[2].role)
        assertEquals("assistant", stored[3].role)

        val request = api.requests.single()
        assertEquals(BASE_URL, request.baseUrl)
        assertEquals(STORED_API_KEY, request.apiKey)
        assertEquals(MODEL, request.model)
        assertEquals(0.7, request.temperature, 1e-6)
        assertNull("未设置思考强度时不应传给接口", request.reasoningEffort)
        assertEquals("system", request.messages[0].role)
        assertEquals("全局人设", request.messages[0].content)
        assertEquals("system", request.messages[1].role)
        assertEquals("会话人设", request.messages[1].content)
        // 历史消息按原顺序排在提示词之后、本轮输入之前。
        assertEquals("user", request.messages[2].role)
        assertEquals("旧问题", request.messages[2].content)
        assertEquals("assistant", request.messages[3].role)
        assertEquals("旧回答", request.messages[3].content)
        assertEquals("user", request.messages.last().role)
        assertEquals("你好呀", request.messages.last().content)
    }

    @Test
    fun sendMessage_injectsMatchedWorldbookEntriesInPriorityOrder() = runBlocking {
        seedConversation(systemPrompt = "会话人设")
        seedPreset()
        worldBookDao.seed(
            conversationId = CONVERSATION_ID,
            title = "高优先级",
            keywords = "剑",
            content = "高优先级条目正文",
            priority = 5,
            enabled = true,
        )
        worldBookDao.seed(
            conversationId = CONVERSATION_ID,
            title = "低优先级",
            keywords = "龙",
            content = "低优先级条目正文",
            priority = 0,
            enabled = true,
        )
        worldBookDao.seed(
            conversationId = CONVERSATION_ID,
            title = "已禁用",
            keywords = "剑",
            content = "禁用条目正文",
            priority = 99,
            enabled = false,
        )
        val api = FakeChatApi(chunks = listOf("好"))

        repository(
            api = api,
            settings = SettingsDataStore.AppSettings(globalPrompt = "全局人设"),
        ).sendMessage(CONVERSATION_ID, "我拔出剑，看到一条龙").toList()

        val requestMessages = api.requests.single().messages
        assertEquals("user", requestMessages.last().role)
        val systemText = requestMessages.dropLast(1)
            .filter { it.role == "system" }
            .joinToString(separator = "\n") { it.content }
        val globalIndex = systemText.indexOf("全局人设")
        val sessionIndex = systemText.indexOf("会话人设")
        val highIndex = systemText.indexOf("高优先级条目正文")
        val lowIndex = systemText.indexOf("低优先级条目正文")
        assertTrue("全局提示词应被注入", globalIndex >= 0)
        assertTrue("会话提示词应排在全局提示词之后", sessionIndex > globalIndex)
        assertTrue("高优先级条目应被注入", highIndex > sessionIndex)
        assertTrue("低优先级条目应排在高优先级条目之后", lowIndex > highIndex)
        assertFalse("禁用条目不应被注入", systemText.contains("禁用条目正文"))
    }

    @Test
    fun sendMessage_withoutPreset_throwsAndKeepsUserMessage() = runBlocking {
        seedConversation(presetId = null)
        val api = FakeChatApi(chunks = listOf("不应出现"))
        val chatRepository = repository(api = api)

        val error = assertThrowsBlocking(IllegalStateException::class.java) {
            chatRepository.sendMessage(CONVERSATION_ID, "hi").toList()
        }

        assertEquals("未配置模型预设，请先在会话设置中选择", error.message)
        val stored = messageDao.all(CONVERSATION_ID)
        assertEquals(1, stored.size)
        assertEquals("user", stored[0].role)
        assertEquals("hi", stored[0].content)
        assertTrue("预设缺失时不应发起网络请求", api.requests.isEmpty())
    }

    @Test
    fun sendMessage_whenApiFailsWithoutOutput_doesNotPersistAssistantMessage() = runBlocking {
        seedConversation()
        seedPreset()
        val api = FakeChatApi(failure = ApiException(Kind.NETWORK, "网络错误"))

        val error = assertThrowsBlocking(ApiException::class.java) {
            repository(api = api).sendMessage(CONVERSATION_ID, "你好").toList()
        }

        assertEquals(Kind.NETWORK, error.kind)
        val stored = messageDao.all(CONVERSATION_ID)
        assertEquals(1, stored.size)
        assertEquals("user", stored[0].role)
        assertEquals("你好", stored[0].content)
    }

    @Test
    fun sendMessage_whenApiFailsAfterPartialOutput_persistsPartialAssistantMessage() = runBlocking {
        seedConversation()
        seedPreset()
        val api = FakeChatApi(
            chunks = listOf("部分", "内容"),
            failure = ApiException(Kind.SERVER, "服务端错误"),
        )

        assertThrowsBlocking(ApiException::class.java) {
            repository(api = api).sendMessage(CONVERSATION_ID, "你好").toList()
        }

        val stored = messageDao.all(CONVERSATION_ID)
        assertEquals(2, stored.size)
        assertEquals("user", stored[0].role)
        assertEquals("assistant", stored[1].role)
        assertEquals("部分内容", stored[1].content)
    }

    @Test
    fun regenerate_deletesLastAssistantAndDoesNotInsertDuplicateUserMessage() = runBlocking {
        seedConversation(title = "已有标题")
        seedPreset()
        messageDao.seed(conversationId = CONVERSATION_ID, role = "user", content = "第一次提问")
        messageDao.seed(conversationId = CONVERSATION_ID, role = "assistant", content = "旧回复")
        val api = FakeChatApi(chunks = listOf("新回复"))

        repository(api = api).regenerate(CONVERSATION_ID).toList()

        val stored = messageDao.all(CONVERSATION_ID)
        assertEquals(2, stored.size)
        assertEquals(listOf("第一次提问", "新回复"), stored.map { it.content })
        assertEquals(1, stored.count { it.role == "user" })
        assertEquals(1, stored.count { it.role == "assistant" })

        val request = api.requests.single()
        assertEquals("user", request.messages.last().role)
        assertEquals("第一次提问", request.messages.last().content)
    }

    @Test
    fun sendMessage_whenTitleBlank_generatesTitleFromUserTextTake20() = runBlocking {
        seedConversation(title = "")
        seedPreset()
        val userText = "这是一个超过二十个字符的非常长的用户消息内容用于测试标题自动生成"
        val api = FakeChatApi(chunks = listOf("回复"))

        repository(api = api).sendMessage(CONVERSATION_ID, userText).toList()

        val conversation = conversationDao.find(CONVERSATION_ID)
        assertNotNull(conversation)
        assertEquals(userText.take(20), conversation?.title)
    }

    @Test
    fun sendMessage_passesReasoningEffortFromPreset() = runBlocking {
        seedConversation()
        seedPreset(reasoningEffort = "medium")
        val api = FakeChatApi(chunks = listOf("好"))

        repository(api = api).sendMessage(CONVERSATION_ID, "你好").toList()

        assertEquals("medium", api.requests.single().reasoningEffort)
    }

    @Test
    fun sendMessage_withoutReasoningEffort_passesNull() = runBlocking {
        seedConversation()
        seedPreset(reasoningEffort = null)
        val api = FakeChatApi(chunks = listOf("好"))

        repository(api = api).sendMessage(CONVERSATION_ID, "你好").toList()

        assertNull(api.requests.single().reasoningEffort)
    }

    @Test
    fun regenerate_keepsReasoningEffortFromPreset() = runBlocking {
        seedConversation(title = "已有标题")
        seedPreset(reasoningEffort = "none")
        messageDao.seed(conversationId = CONVERSATION_ID, role = "user", content = "第一次提问")
        messageDao.seed(conversationId = CONVERSATION_ID, role = "assistant", content = "旧回复")
        val api = FakeChatApi(chunks = listOf("新回复"))

        repository(api = api).regenerate(CONVERSATION_ID).toList()

        assertEquals("none", api.requests.single().reasoningEffort)
    }

    @Test
    fun sendMessage_includesSessionPostHistoryPromptAsLastSystemMessage() = runBlocking {
        seedConversation(systemPrompt = "会话人设", postHistoryPrompt = "把{{user}}称作队长，并输出状态栏")
        seedPreset()
        val api = FakeChatApi(chunks = listOf("好"))

        repository(api = api).sendMessage(CONVERSATION_ID, "你好").toList()

        val request = api.requests.single()
        // 后置提示词是整段消息的最后一条 system，且位于本轮用户输入之后
        assertEquals("system", request.messages.last().role)
        assertEquals("把用户称作队长，并输出状态栏", request.messages.last().content)
        val beforePost = request.messages[request.messages.size - 2]
        assertEquals("user", beforePost.role)
        assertEquals("你好", beforePost.content)
    }

    // ---------------------------------------------------------------------
    // 测试辅助
    // ---------------------------------------------------------------------

    private fun repository(
        api: ChatApi,
        settings: SettingsDataStore.AppSettings = SettingsDataStore.AppSettings(),
    ): ChatRepository = ChatRepository(
        conversationDao = conversationDao,
        messageDao = messageDao,
        worldBookDao = worldBookDao,
        presetDao = presetDao,
        settings = FakeSettingsRepository(settings),
        api = api,
        keyDecryptor = { stored -> stored },
        now = { FIXED_NOW },
    )

    private fun seedConversation(
        title: String = "",
        presetId: Long? = PRESET_ID,
        systemPrompt: String = "",
        postHistoryPrompt: String = "",
        scanCount: Int = 20,
        recursionDepth: Int = 2,
    ) {
        conversationDao.seed(
            ConversationEntity(
                id = CONVERSATION_ID,
                title = title,
                presetId = presetId,
                systemPrompt = systemPrompt,
                postHistoryPrompt = postHistoryPrompt,
                scanCount = scanCount,
                recursionDepth = recursionDepth,
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )
    }

    private fun seedPreset(reasoningEffort: String? = null) {
        presetDao.seed(
            ModelPresetEntity(
                id = PRESET_ID,
                name = "测试预设",
                baseUrl = BASE_URL,
                apiKeyEncrypted = STORED_API_KEY,
                model = MODEL,
                reasoningEffort = reasoningEffort,
            ),
        )
    }

    private fun <T : Throwable> assertThrowsBlocking(
        expected: Class<T>,
        block: suspend () -> Unit,
    ): T = assertThrows(expected) {
        runBlocking { block() }
    }

    private companion object {
        /** 测试会话 id。 */
        const val CONVERSATION_ID = 1L

        /** 测试预设 id。 */
        const val PRESET_ID = 10L

        /** 测试 BaseURL。 */
        const val BASE_URL = "https://api.example.test/v1"

        /** 测试模型名。 */
        const val MODEL = "test-model"

        /** 测试用 API Key 密文（假解密器原样返回）。 */
        const val STORED_API_KEY = "encrypted-api-key"

        /** 测试中固定的时间戳，保证落库时间可预期。 */
        const val FIXED_NOW = 1_000L
    }
}

// -------------------------------------------------------------------------
// 内存假 DAO / 假服务实现（仅用于纯 JVM 单元测试）
// -------------------------------------------------------------------------

/** 内存版 [ConversationDao]。 */
private class FakeConversationDao : ConversationDao {

    private val items = mutableListOf<ConversationEntity>()
    private var nextId = 1L

    fun seed(entity: ConversationEntity): ConversationEntity {
        val stored = if (entity.id == 0L) entity.copy(id = nextId++) else entity
        items.removeAll { it.id == stored.id }
        items.add(stored)
        if (stored.id >= nextId) nextId = stored.id + 1
        return stored
    }

    fun find(id: Long): ConversationEntity? = items.firstOrNull { it.id == id }

    override fun observeAll(): Flow<List<ConversationEntity>> = flowOf(items.toList())

    override fun observeById(id: Long): Flow<ConversationEntity?> = flowOf(find(id))

    override suspend fun getById(id: Long): ConversationEntity? = find(id)

    override suspend fun insert(entity: ConversationEntity): Long {
        val stored = if (entity.id == 0L) entity.copy(id = nextId++) else entity
        items.add(stored)
        if (stored.id >= nextId) nextId = stored.id + 1
        return stored.id
    }

    override suspend fun update(entity: ConversationEntity) {
        val index = items.indexOfFirst { it.id == entity.id }
        if (index >= 0) items[index] = entity else items.add(entity)
    }

    override suspend fun delete(entity: ConversationEntity) {
        items.removeAll { it.id == entity.id }
    }

    override suspend fun deleteById(id: Long) {
        items.removeAll { it.id == id }
    }
}

/** 内存版 [PresetDao]。 */
private class FakePresetDao : PresetDao {

    private val items = mutableListOf<ModelPresetEntity>()
    private var nextId = 1L

    fun seed(entity: ModelPresetEntity) {
        val stored = if (entity.id == 0L) entity.copy(id = nextId++) else entity
        items.add(stored)
        if (stored.id >= nextId) nextId = stored.id + 1
    }

    override suspend fun upsert(entity: ModelPresetEntity) {
        if (entity.id == 0L) {
            items.add(entity.copy(id = nextId++))
        } else {
            val index = items.indexOfFirst { it.id == entity.id }
            if (index >= 0) {
                items[index] = entity
            } else {
                items.add(entity)
                if (entity.id >= nextId) nextId = entity.id + 1
            }
        }
    }

    override fun observeAll(): Flow<List<ModelPresetEntity>> = flowOf(items.toList())

    override suspend fun getAll(): List<ModelPresetEntity> = items.toList()

    override suspend fun getById(id: Long): ModelPresetEntity? = items.firstOrNull { it.id == id }

    override suspend fun delete(entity: ModelPresetEntity) {
        items.removeAll { it.id == entity.id }
    }
}

/** 内存版 [MessageDao]。 */
private class FakeMessageDao : MessageDao {

    private val items = mutableListOf<MessageEntity>()
    private var nextId = 1L

    fun seed(conversationId: Long, role: String, content: String): MessageEntity {
        val stored = MessageEntity(
            id = nextId++,
            conversationId = conversationId,
            role = role,
            content = content,
            createdAt = 0L,
        )
        items.add(stored)
        return stored
    }

    fun all(conversationId: Long): List<MessageEntity> =
        items.filter { it.conversationId == conversationId }.sortedBy { it.id }

    override fun observeByConversation(conversationId: Long): Flow<List<MessageEntity>> =
        flowOf(all(conversationId))

    override suspend fun getByConversation(conversationId: Long): List<MessageEntity> = all(conversationId)

    override suspend fun insert(entity: MessageEntity): Long {
        val id = if (entity.id == 0L) nextId++ else entity.id
        items.add(entity.copy(id = id))
        if (id >= nextId) nextId = id + 1
        return id
    }

    override suspend fun deleteByIdAndLater(conversationId: Long, messageId: Long) {
        items.removeAll { it.conversationId == conversationId && it.id >= messageId }
    }

    override suspend fun deleteLastAssistant(conversationId: Long) {
        val lastAssistant = items
            .filter { it.conversationId == conversationId && it.role == "assistant" }
            .maxByOrNull { it.id }
            ?: return
        items.removeAll { it.id == lastAssistant.id }
    }

    override suspend fun getLastMessage(conversationId: Long): MessageEntity? =
        items.filter { it.conversationId == conversationId }.maxByOrNull { it.id }
}

/** 内存版 [WorldBookDao]。 */
private class FakeWorldBookDao : WorldBookDao {

    private val items = mutableListOf<WorldBookEntryEntity>()
    private var nextId = 1L

    fun seed(
        conversationId: Long,
        title: String,
        keywords: String,
        content: String,
        priority: Int,
        enabled: Boolean,
    ) {
        items.add(
            WorldBookEntryEntity(
                id = nextId++,
                conversationId = conversationId,
                title = title,
                keywords = keywords,
                content = content,
                priority = priority,
                enabled = enabled,
            ),
        )
    }

    private fun enabledOf(conversationId: Long): List<WorldBookEntryEntity> =
        items.filter { it.conversationId == conversationId && it.enabled }
            .sortedWith(compareByDescending<WorldBookEntryEntity> { it.priority }.thenBy { it.id })

    override fun observeByConversation(conversationId: Long): Flow<List<WorldBookEntryEntity>> =
        flowOf(
            items.filter { it.conversationId == conversationId }
                .sortedWith(compareByDescending<WorldBookEntryEntity> { it.priority }.thenBy { it.id }),
        )

    override suspend fun getById(id: Long): WorldBookEntryEntity? = items.firstOrNull { it.id == id }

    override suspend fun getEnabledByConversation(conversationId: Long): List<WorldBookEntryEntity> =
        enabledOf(conversationId)

    override suspend fun insert(entity: WorldBookEntryEntity): Long {
        val id = if (entity.id == 0L) nextId++ else entity.id
        items.add(entity.copy(id = id))
        if (id >= nextId) nextId = id + 1
        return id
    }

    override suspend fun update(entity: WorldBookEntryEntity) {
        val index = items.indexOfFirst { it.id == entity.id }
        if (index >= 0) items[index] = entity else items.add(entity)
    }

    override suspend fun delete(entity: WorldBookEntryEntity) {
        items.removeAll { it.id == entity.id }
    }
}

/** 固定返回指定快照的内存版 [SettingsRepository]。 */
private class FakeSettingsRepository(
    private val fixed: SettingsDataStore.AppSettings,
) : SettingsRepository {

    override val settingsFlow: Flow<SettingsDataStore.AppSettings> = MutableStateFlow(fixed)

    override suspend fun current(): SettingsDataStore.AppSettings = fixed

    override suspend fun setGlobalPrompt(v: String) = Unit

    override suspend fun setContextLimitChars(v: Int) = Unit

    override suspend fun setTemperature(v: Float) = Unit

    override suspend fun setDefaultPresetId(v: Long?) = Unit
}

/** 记录请求参数、按预设脚本产出增量或抛出异常的假 [ChatApi]。 */
private class FakeChatApi(
    private val chunks: List<String> = emptyList(),
    private val failure: Exception? = null,
) : ChatApi {

    val requests = mutableListOf<RecordedRequest>()

    override fun chatStream(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        temperature: Double,
        reasoningEffort: String?,
    ): Flow<String> = flow {
        requests.add(RecordedRequest(baseUrl, apiKey, model, messages, temperature, reasoningEffort))
        for (chunk in chunks) {
            emit(chunk)
        }
        failure?.let { throw it }
    }
}

/** 假 [ChatApi] 记录下来的单次请求参数。 */
private data class RecordedRequest(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double,
    val reasoningEffort: String?,
)
