package com.myagent.app.data.repository

import com.myagent.app.data.db.dao.ConversationDao
import com.myagent.app.data.db.dao.MessageDao
import com.myagent.app.data.db.dao.WorldBookDao
import com.myagent.app.data.db.entity.ConversationEntity
import com.myagent.app.data.db.entity.MessageEntity
import com.myagent.app.data.db.entity.WorldBookEntryEntity
import com.myagent.app.domain.import.CharacterCard
import com.myagent.app.domain.import.CharacterCardParser
import com.myagent.app.domain.import.WorldBook
import com.myagent.app.domain.import.WorldBookEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [CharacterCardImporter] 的纯 JVM 单元测试（内存假 DAO）。
 */
class CharacterCardImporterTest {

    private val conversationDao = FakeConversationDao()
    private val worldBookDao = FakeWorldBookDao()
    private val messageDao = FakeMessageDao()
    private val importer = CharacterCardImporter(
        conversationRepository = ConversationRepository(conversationDao),
        worldbookRepository = WorldbookRepository(worldBookDao),
        messageRepository = MessageRepository(messageDao),
    )

    @Test
    fun import_withSyntheticCard_mapsFieldsAndImportsKeywordlessEntries() = runBlocking {
        val card = CharacterCard(
            name = "测试角色",
            description = "{{char}}是一名剑士",
            personality = "勇敢",
            scenario = "边境小镇",
            firstMes = "（应被忽略的开场白）",
            mesExample = "示例对话",
            postHistoryInstructions = "后置要求：{{char}}要冷静",
            alternateGreetings = listOf("开场白一", "开场白二"),
            worldBook = WorldBook(
                scanDepth = 4,
                recursiveScanning = false,
                entries = listOf(
                    WorldBookEntry(keys = emptyList(), content = "无关键词条目"),
                    WorldBookEntry(keys = listOf("剑|刀"), content = "武器条目"),
                    WorldBookEntry(keys = listOf("龙"), content = "巨龙条目"),
                ),
            ),
        )

        val result = importer.import(
            CharacterCardImporter.Options(
                card = card,
                selectedGreetingIndexes = setOf(1),
                customOpening = "我的开场",
            ),
        )

        // 会话基础字段
        val conversation = conversationDao.find(result.conversationId)
        assertNotNull(conversation)
        assertEquals("测试角色", conversation?.title)
        assertEquals("用户", conversation?.userName)
        assertEquals(4, conversation?.scanCount)
        assertEquals(0, conversation?.recursionDepth)
        // 提示词：firstMes 忽略、{{char}} 已替换、空字段跳过
        val prompt = conversation?.systemPrompt.orEmpty()
        assertTrue(prompt.contains("测试角色是一名剑士"))
        assertFalse(prompt.contains("（应被忽略的开场白）"))
        assertFalse(prompt.contains("{{char}}"))
        assertTrue(prompt.contains("勇敢"))
        assertTrue(prompt.contains("边境小镇"))
        assertTrue(prompt.contains("示例对话"))
        // 卡片 post_history_instructions → 会话后置提示词（{{char}} 已替换）
        assertEquals("后置要求：测试角色要冷静", conversation?.postHistoryPrompt)

        // 世界书：3 条全部导入；无关键词条目以空关键词导入，关键词按 | 拆分
        assertEquals(3, result.importedWorldbookCount)
        assertEquals(1, result.keywordlessWorldbookCount)
        val saved = worldBookDao.all(result.conversationId)
        assertEquals(3, saved.size)
        val keywordlessEntry = saved.first { it.content == "无关键词条目" }
        assertEquals("", keywordlessEntry.keywords)
        val weaponEntry = saved.first { it.content == "武器条目" }
        assertEquals(listOf("剑", "刀"), weaponEntry.keywords.split('\n'))

        // 消息：勾选的第二条开场白（AI）+ 自定义开场（user）
        val messages = messageDao.all(result.conversationId)
        assertEquals(2, messages.size)
        assertEquals("assistant", messages[0].role)
        assertEquals("开场白二", messages[0].content)
        assertEquals("user", messages[1].role)
        assertEquals("我的开场", messages[1].content)
    }

    @Test
    fun import_realCardIfPresent_mapsAllFields() = runBlocking {
        val file = listOf(
            File("D:/Codex/MyAgent_android/艾诺迪亚大陆.png"),
            File("../艾诺迪亚大陆.png"),
        ).firstOrNull { it.isFile }
        if (file == null) {
            println("SKIP")
            return@runBlocking
        }
        val card = CharacterCardParser.parse(file.readBytes())

        val result = importer.import(CharacterCardImporter.Options(card = card))

        assertEquals("艾诺迪亚大陆", conversationDao.find(result.conversationId)?.title)
        // 16 条全部导入，其中 7 条无关键词
        assertEquals(16, result.importedWorldbookCount)
        assertEquals(7, result.keywordlessWorldbookCount)
        val savedEntries = worldBookDao.all(result.conversationId)
        assertEquals(16, savedEntries.size)
        assertEquals(7, savedEntries.count { it.keywords.isBlank() })
        // 帝国条目：keys "布里塔尼亚帝国|布里塔尼亚" 拆为两个关键词
        val empireEntry = savedEntries.first {
            it.keywords.split('\n').contains("布里塔尼亚帝国")
        }
        assertEquals(listOf("布里塔尼亚帝国", "布里塔尼亚"), empireEntry.keywords.split('\n'))
        assertTrue(conversationDao.find(result.conversationId)?.systemPrompt.orEmpty().contains("RPG冒险故事系统"))
        // 本卡 post_history_instructions 为空，后置提示词应为空串
        assertEquals("", conversationDao.find(result.conversationId)?.postHistoryPrompt)
    }

    // ---------------------------------------------------------------------
    // 内存假 DAO
    // ---------------------------------------------------------------------

    private class FakeConversationDao : ConversationDao {
        private val items = mutableListOf<ConversationEntity>()
        private var nextId = 1L

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

    private class FakeWorldBookDao : WorldBookDao {
        private val items = mutableListOf<WorldBookEntryEntity>()
        private var nextId = 1L

        fun all(conversationId: Long): List<WorldBookEntryEntity> =
            items.filter { it.conversationId == conversationId }.sortedBy { it.id }

        override fun observeByConversation(conversationId: Long): Flow<List<WorldBookEntryEntity>> =
            flowOf(all(conversationId))

        override suspend fun getById(id: Long): WorldBookEntryEntity? = items.firstOrNull { it.id == id }

        override suspend fun getEnabledByConversation(conversationId: Long): List<WorldBookEntryEntity> =
            all(conversationId).filter { it.enabled }

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

    private class FakeMessageDao : MessageDao {
        private val items = mutableListOf<MessageEntity>()
        private var nextId = 1L

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
            val last = items.filter { it.conversationId == conversationId && it.role == "assistant" }
                .maxByOrNull { it.id }
                ?: return
            items.removeAll { it.id == last.id }
        }

        override suspend fun getLastMessage(conversationId: Long): MessageEntity? =
            items.filter { it.conversationId == conversationId }.maxByOrNull { it.id }
    }
}
