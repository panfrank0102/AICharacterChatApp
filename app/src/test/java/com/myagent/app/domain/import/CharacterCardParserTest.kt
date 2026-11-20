package com.myagent.app.domain.import

import java.io.File
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CharacterCardParser] 的单元测试。
 *
 * 覆盖 V1/V2/V3 卡片、PNG tEXt 提取、纯 JSON 输入、错误分支以及真实卡片回归测试。
 */
class CharacterCardParserTest {

    private val v1CardJson = """
        {
          "name": "V1 角色",
          "description": "V1 描述",
          "personality": "冷静",
          "scenario": "古老城堡",
          "first_mes": "你好，旅行者。",
          "mes_example": "<START>\n{{user}}: 你好",
          "system_prompt": "系统提示",
          "post_history_instructions": "后置提示",
          "creator": "tester",
          "character_version": "1.0",
          "alternate_greetings": ["早上好", "晚上好"],
          "character_book": {
            "name": "V1 世界书",
            "scan_depth": 3,
            "token_budget": 512,
            "recursive_scanning": true,
            "entries": [
              {
                "keys": ["艾诺迪亚", "大陆"],
                "secondary_keys": ["王国"],
                "content": "这是第一条世界书内容。",
                "priority": 10,
                "enabled": true,
                "constant": true,
                "selective": true,
                "case_sensitive": true,
                "comment": "第一条备注"
              },
              {
                "keys": [],
                "secondary_keys": [],
                "content": "第二条内容",
                "priority": 20,
                "enabled": false,
                "constant": false,
                "selective": false,
                "case_sensitive": false,
                "comment": "第二条备注"
              }
            ]
          }
        }
    """.trimIndent()

    private val v2CardJson = """
        {
          "spec": "chara_card_v2",
          "spec_version": "2.0",
          "name": "外层名字",
          "data": {
            "name": "V2 角色",
            "description": "V2 描述",
            "personality": "V2 性格",
            "first_mes": "V2 第一条消息"
          }
        }
    """.trimIndent()

    private val v3CardJson = """
        {
          "spec": "chara_card_v3",
          "spec_version": "3.0",
          "data": {
            "name": "V3 角色",
            "description": "V3 描述",
            "first_mes": "V3 第一条消息"
          }
        }
    """.trimIndent()

    private val pngSignature = byteArrayOf(
        0x89.toByte(),
        0x50.toByte(),
        0x4E.toByte(),
        0x47.toByte(),
        0x0D.toByte(),
        0x0A.toByte(),
        0x1A.toByte(),
        0x0A.toByte(),
    )

    @Test
    fun parse_v1CharaPng_mapsAllCardFields() {
        val card = CharacterCardParser.parse(makeCardPng("chara", v1CardJson))

        assertEquals("V1 角色", card.name)
        assertEquals("V1 描述", card.description)
        assertEquals("冷静", card.personality)
        assertEquals("古老城堡", card.scenario)
        assertEquals("你好，旅行者。", card.firstMes)
        assertEquals("<START>\n{{user}}: 你好", card.mesExample)
        assertEquals("系统提示", card.systemPrompt)
        assertEquals("后置提示", card.postHistoryInstructions)
        assertEquals("tester", card.creator)
        assertEquals("1.0", card.characterVersion)
        assertEquals(listOf("早上好", "晚上好"), card.alternateGreetings)

        val worldBook = card.worldBook
        assertNotNull(worldBook)
        if (worldBook == null) return

        assertEquals("V1 世界书", worldBook.name)
        assertEquals(3, worldBook.scanDepth)
        assertEquals(512, worldBook.tokenBudget)
        assertEquals(true, worldBook.recursiveScanning)
        assertEquals(2, worldBook.entries.size)

        val first = worldBook.entries[0]
        assertEquals(listOf("艾诺迪亚", "大陆"), first.keys)
        assertEquals(listOf("王国"), first.secondaryKeys)
        assertEquals("这是第一条世界书内容。", first.content)
        assertEquals(10, first.priority)
        assertTrue(first.enabled)
        assertTrue(first.constant)
        assertTrue(first.selective)
        assertTrue(first.caseSensitive)
        assertEquals("第一条备注", first.comment)

        val second = worldBook.entries[1]
        assertEquals(emptyList<String>(), second.keys)
        assertEquals(emptyList<String>(), second.secondaryKeys)
        assertEquals("第二条内容", second.content)
        assertEquals(20, second.priority)
        assertFalse(second.enabled)
        assertFalse(second.constant)
        assertFalse(second.selective)
        assertFalse(second.caseSensitive)
        assertEquals("第二条备注", second.comment)
    }

    @Test
    fun parse_v2Ccv3Png_usesWrappedData() {
        val card = CharacterCardParser.parse(makeCardPng("ccv3", v2CardJson))

        assertEquals("V2 角色", card.name)
        assertEquals("V2 描述", card.description)
        assertEquals("V2 性格", card.personality)
        assertEquals("V2 第一条消息", card.firstMes)
    }

    @Test
    fun parse_v3Ccv3Png_usesWrappedData() {
        val card = CharacterCardParser.parse(makeCardPng("ccv3", v3CardJson))

        assertEquals("V3 角色", card.name)
        assertEquals("V3 描述", card.description)
        assertEquals("V3 第一条消息", card.firstMes)
    }

    @Test
    fun parse_whenCcv3AndCharaBothExist_prefersCcv3() {
        val charaJson = """
            {
              "name": "来自 chara 的角色",
              "description": "来自 chara 的描述"
            }
        """.trimIndent()
        val ccv3Json = """
            {
              "spec": "chara_card_v2",
              "spec_version": "2.0",
              "data": {
                "name": "来自 ccv3 的角色",
                "description": "来自 ccv3 的描述"
              }
            }
        """.trimIndent()

        val png = makePngWithChunks(
            makePngChunk("tEXt", makeTExt("chara", encodeJson(charaJson))),
            makePngChunk("tEXt", makeTExt("ccv3", encodeJson(ccv3Json))),
        )

        val card = CharacterCardParser.parse(png)
        assertEquals("来自 ccv3 的角色", card.name)
        assertEquals("来自 ccv3 的描述", card.description)
    }

    @Test
    fun parse_plainJsonBytes_succeeds() {
        val json = """
            {
              "name": "纯 JSON 角色",
              "description": "没有 PNG 外层"
            }
        """.trimIndent()

        val card = CharacterCardParser.parse(json.toByteArray(Charsets.UTF_8))
        assertEquals("纯 JSON 角色", card.name)
        assertEquals("没有 PNG 外层", card.description)
    }

    @Test
    fun parse_entryWithEmptyKeys_succeeds() {
        val json = """
            {
              "spec": "chara_card_v2",
              "spec_version": "2.0",
              "data": {
                "name": "空关键词角色",
                "character_book": {
                  "entries": [
                    {
                      "keys": [],
                      "secondary_keys": [],
                      "content": "没有关键词的条目"
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val card = CharacterCardParser.parse(makeCardPng("ccv3", json))
        val worldBook = card.worldBook
        assertNotNull(worldBook)
        if (worldBook == null) return

        assertEquals(1, worldBook.entries.size)
        assertTrue(worldBook.entries[0].keys.isEmpty())
        assertTrue(worldBook.entries[0].secondaryKeys.isEmpty())
        assertEquals("没有关键词的条目", worldBook.entries[0].content)
    }

    @Test
    fun parse_nonCardJson_throws() {
        val error = assertThrows(CharacterCardParseException::class.java) {
            CharacterCardParser.parse("""{"foo":"bar"}""".toByteArray(Charsets.UTF_8))
        }
        assertEquals("不是有效的角色卡文件", error.message)
    }

    @Test
    fun parse_badBase64_throws() {
        val png = makePngWithChunks(
            makePngChunk("tEXt", makeTExt("chara", "!!!not-base64!!!")),
        )

        val error = assertThrows(CharacterCardParseException::class.java) {
            CharacterCardParser.parse(png)
        }
        assertEquals("角色卡数据解码失败", error.message)
    }

    @Test
    fun parse_pngWithoutCardChunk_throws() {
        val png = makePngWithChunks(
            makePngChunk("tEXt", makeTExt("Comment", "just a comment")),
        )

        val error = assertThrows(CharacterCardParseException::class.java) {
            CharacterCardParser.parse(png)
        }
        assertEquals("不是有效的角色卡文件（缺少角色卡数据）", error.message)
    }

    @Test
    fun parse_realCardIfPresent() {
        val candidates = listOf(
            File("D:/Codex/MyAgent_android/艾诺迪亚大陆.png"),
            File("../艾诺迪亚大陆.png"),
        )
        val file = candidates.firstOrNull { candidate -> candidate.isFile }
        if (file == null) {
            println("SKIP")
            return
        }

        val card = CharacterCardParser.parse(file.readBytes())
        assertEquals("艾诺迪亚大陆", card.name)

        val worldBook = card.worldBook
        assertNotNull(worldBook)
        if (worldBook == null) return
        assertEquals(16, worldBook.entries.size)

        val emptyKeyEntryCount = worldBook.entries.count { entry ->
            (entry.keys + entry.secondaryKeys).all { key -> key.isBlank() }
        }
        assertEquals(7, emptyKeyEntryCount)
        assertEquals(2, card.alternateGreetings.size)
    }

    /** 生成一个 PNG chunk：4 字节大端长度 + 4 字节类型 + payload + 4 字节零 CRC。 */
    private fun makePngChunk(type: String, payload: ByteArray): ByteArray {
        require(type.length == 4) { "PNG chunk type 必须为 4 个字符" }
        val chunk = ByteArray(4 + 4 + payload.size + 4)
        writeBigEndianInt(chunk, 0, payload.size)
        type.toByteArray(Charsets.ISO_8859_1).copyInto(chunk, 4)
        payload.copyInto(chunk, 8)
        return chunk
    }

    /** 生成 tEXt 块数据：keyword + 0x00 + value，均按 ISO-8859-1 编码。 */
    private fun makeTExt(keyword: String, value: String): ByteArray =
        keyword.toByteArray(Charsets.ISO_8859_1) +
            byteArrayOf(0.toByte()) +
            value.toByteArray(Charsets.ISO_8859_1)

    /** 生成带 IHDR 占位块与单个角色卡 tEXt 块的最小 PNG。 */
    private fun makeCardPng(keyword: String, json: String): ByteArray {
        val text = makeTExt(keyword, encodeJson(json))
        return makePngWithChunks(makePngChunk("tEXt", text))
    }

    /** PNG 签名 + IHDR 占位块 + 给定 chunk 列表。 */
    private fun makePngWithChunks(vararg chunks: ByteArray): ByteArray {
        val header = pngSignature + makePngChunk("IHDR", ByteArray(13))
        return chunks.fold(header) { result, chunk -> result + chunk }
    }

    /** 与解析器约定一致：JSON 按 UTF-8 编码后做标准 base64。 */
    private fun encodeJson(json: String): String =
        Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))

    /** 写入 4 字节大端整数。 */
    private fun writeBigEndianInt(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 24).toByte()
        target[offset + 1] = (value ushr 16).toByte()
        target[offset + 2] = (value ushr 8).toByte()
        target[offset + 3] = value.toByte()
    }
}
