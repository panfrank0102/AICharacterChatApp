package com.myagent.app.domain.import

import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * 酒馆（SillyTavern）角色卡解析器。
 *
 * 支持两类输入：
 * - PNG 角色卡：卡数据先 base64 编码，再写入 PNG 的 tEXt/iTXt 文本块。
 *   V1 使用关键字 `chara`，解码后是裸卡字段 JSON；
 *   V2/V3 使用关键字 `ccv3`，解码后是 `{"spec":..., "spec_version":..., "data":{...}}` 包装。
 * - 同名 `.json` 文件：内容可能是 V1 裸结构，也可能是 V2/V3 的 spec + data 包装。
 *
 * 解析结果统一映射为 [CharacterCard]；输入不合法时抛出 [CharacterCardParseException]。
 */
object CharacterCardParser {

    /** 宽松 JSON 配置：酒馆导出文件偶尔带有额外字段或不够严格的写法。 */
    private val JSON = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** PNG 文件签名：89 50 4E 47 0D 0A 1A 0A。 */
    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(),
        0x50.toByte(),
        0x4E.toByte(),
        0x47.toByte(),
        0x0D.toByte(),
        0x0A.toByte(),
        0x1A.toByte(),
        0x0A.toByte(),
    )

    /**
     * 解析角色卡字节数据。
     *
     * @param bytes PNG 图片字节或 UTF-8 编码的 JSON 文本
     * @return 解析后的角色卡
     * @throws CharacterCardParseException 数据不是有效角色卡或无法解码
     */
    fun parse(bytes: ByteArray): CharacterCard {
        // 以 PNG 签名为准选择解析路径；非 PNG 输入按 JSON 文件处理。
        if (isPng(bytes)) {
            return parsePng(bytes)
        }
        val jsonText = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
        return parseJsonText(jsonText)
    }

    /** 判断字节数组是否以 PNG 签名开头。 */
    private fun isPng(bytes: ByteArray): Boolean {
        if (bytes.size < PNG_SIGNATURE.size) return false
        return PNG_SIGNATURE.indices.all { index -> bytes[index] == PNG_SIGNATURE[index] }
    }

    /**
     * 从 PNG 的 chunk 流中提取角色卡文本：
     * 优先使用 `ccv3`（V2/V3），其次使用 `chara`（V1）。
     */
    private fun parsePng(bytes: ByteArray): CharacterCard {
        var position = PNG_SIGNATURE.size
        var ccv3Text: String? = null
        var charaText: String? = null

        // 每个 chunk：4 字节大端长度 + 4 字节类型 + length 字节数据 + 4 字节 CRC。
        // CRC 不做校验，按结构跳过即可。
        while (bytes.size - position >= 8) {
            val length = readBigEndianInt(bytes, position)
            val type = String(bytes, position + 4, 4, Charsets.ISO_8859_1)
            val dataStart = position + 8
            val dataEndLong = dataStart.toLong() + length.toLong()
            if (length < 0 || dataEndLong + 4L > bytes.size.toLong()) {
                throw CharacterCardParseException("不是有效的角色卡文件（PNG 数据不完整）")
            }
            val dataEnd = dataEndLong.toInt()

            when (type) {
                "tEXt" -> {
                    // tEXt 数据：keyword + 0x00 + 文本，两段均按 ISO-8859-1 解码。
                    val entry = parseTExtChunk(bytes, dataStart, dataEnd)
                    if (entry != null) {
                        when (entry.first) {
                            "ccv3" -> ccv3Text = ccv3Text ?: entry.second
                            "chara" -> charaText = charaText ?: entry.second
                        }
                    }
                }

                "iTXt" -> {
                    // iTXt 文本本身为 UTF-8；压缩块（compression flag = 1）直接跳过。
                    val entry = parseITxtChunk(bytes, dataStart, dataEnd)
                    if (entry != null) {
                        when (entry.first) {
                            "ccv3" -> ccv3Text = ccv3Text ?: entry.second
                            "chara" -> charaText = charaText ?: entry.second
                        }
                    }
                }
            }

            position = dataEnd + 4
        }

        val encodedJson = ccv3Text ?: charaText
            ?: throw CharacterCardParseException("不是有效的角色卡文件（缺少角色卡数据）")
        val jsonText = decodeBase64(encodedJson)
        return parseJsonText(jsonText)
    }

    /** 读取 4 字节大端有符号整数（PNG 的 length 字段）。 */
    private fun readBigEndianInt(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    /** 在 [start, end) 范围内查找 0x00 分隔符，找不到返回 -1。 */
    private fun indexOfZero(bytes: ByteArray, start: Int, end: Int): Int {
        for (index in start until end) {
            if (bytes[index].toInt() == 0) return index
        }
        return -1
    }

    /** 解析 tEXt 块，返回 keyword 与文本。 */
    private fun parseTExtChunk(bytes: ByteArray, start: Int, end: Int): Pair<String, String>? {
        val separator = indexOfZero(bytes, start, end)
        if (separator < 0) return null
        val keyword = String(bytes, start, separator - start, Charsets.ISO_8859_1)
        val value = String(bytes, separator + 1, end - separator - 1, Charsets.ISO_8859_1)
        return keyword to value
    }

    /**
     * 解析 iTXt 块，返回 keyword 与 UTF-8 文本。
     *
     * 结构：keyword + 0x00 + compressionFlag + compressionMethod +
     * languageTag + 0x00 + translatedKeyword + 0x00 + text。
     * compressionFlag 为 1 表示 zlib 压缩，当前直接跳过该块。
     */
    private fun parseITxtChunk(bytes: ByteArray, start: Int, end: Int): Pair<String, String>? {
        val keywordEnd = indexOfZero(bytes, start, end)
        if (keywordEnd < 0) return null
        val keyword = String(bytes, start, keywordEnd - start, Charsets.ISO_8859_1)

        var cursor = keywordEnd + 1
        if (cursor + 2 > end) return null
        val compressionFlag = bytes[cursor].toInt() and 0xFF
        cursor += 2
        if (compressionFlag != 0) return null

        val languageEnd = indexOfZero(bytes, cursor, end)
        if (languageEnd < 0) return null
        cursor = languageEnd + 1

        val translatedKeywordEnd = indexOfZero(bytes, cursor, end)
        if (translatedKeywordEnd < 0) return null
        cursor = translatedKeywordEnd + 1

        val value = String(bytes, cursor, end - cursor, Charsets.UTF_8)
        return keyword to value
    }

    /** 去掉首尾空白后做 base64 解码，再把结果按 UTF-8 还原成 JSON 文本。 */
    private fun decodeBase64(encoded: String): String {
        return try {
            val decoded = Base64.getDecoder().decode(encoded.trim())
            String(decoded, Charsets.UTF_8)
        } catch (error: IllegalArgumentException) {
            throw CharacterCardParseException("角色卡数据解码失败")
        }
    }

    /**
     * 解析角色卡 JSON。
     *
     * 先按 V2/V3 包装（spec + data）尝试；若没有有效 data，
     * 再把同一份 JSON 按 V1 裸结构解析。
     */
    private fun parseJsonText(text: String): CharacterCard {
        val normalized = text.removePrefix("\uFEFF").trim()
        val cardData = try {
            val envelope = JSON.decodeFromString(CardEnvelope.serializer(), normalized)
            val wrapped = envelope.data
            if (wrapped != null &&
                (wrapped.name.isNotEmpty() ||
                    wrapped.description.isNotEmpty() ||
                    wrapped.characterBook != null)
            ) {
                wrapped
            } else {
                // V1：chara 块 / 裸 JSON 直接就是卡字段。
                JSON.decodeFromString(CardData.serializer(), normalized)
            }
        } catch (error: SerializationException) {
            throw CharacterCardParseException("角色卡解析失败：${briefMessage(error)}")
        }

        val card = cardData.toCharacterCard()
        if (card.name.isBlank() && card.description.isBlank() && card.worldBook == null) {
            throw CharacterCardParseException("不是有效的角色卡文件")
        }
        return card
    }

    /** 提取序列化异常的第一行有效信息，避免把整段 JSON 片段暴露给调用方。 */
    private fun briefMessage(error: SerializationException): String {
        val message = error.message.orEmpty()
        val firstLine = message.lineSequence().firstOrNull { it.isNotBlank() }
            ?: return "JSON 格式不兼容"
        return firstLine.take(160)
    }

    /** V2/V3 的外层包装。 */
    @Serializable
    private data class CardEnvelope(
        val spec: String? = null,
        @SerialName("spec_version") val specVersion: String? = null,
        val data: CardData? = null,
    )

    /** 卡字段本身；V1 顶层结构与 V2/V3 的 data 对象共用。 */
    @Serializable
    private data class CardData(
        val name: String = "",
        val description: String = "",
        val personality: String = "",
        val scenario: String = "",
        @SerialName("first_mes") val firstMes: String = "",
        @SerialName("mes_example") val mesExample: String = "",
        @SerialName("system_prompt") val systemPrompt: String = "",
        @SerialName("post_history_instructions") val postHistoryInstructions: String = "",
        val creator: String = "",
        @SerialName("character_version") val characterVersion: String = "",
        @SerialName("alternate_greetings") val alternateGreetings: List<String> = emptyList(),
        @SerialName("character_book") val characterBook: CharacterBookData? = null,
    )

    /** 角色书（character_book）字段。 */
    @Serializable
    private data class CharacterBookData(
        val name: String = "",
        @SerialName("scan_depth") val scanDepth: Int? = null,
        @SerialName("token_budget") val tokenBudget: Int? = null,
        @SerialName("recursive_scanning") val recursiveScanning: Boolean? = null,
        val entries: List<BookEntryData> = emptyList(),
    )

    /** 角色书中的单条条目。 */
    @Serializable
    private data class BookEntryData(
        val keys: List<String> = emptyList(),
        @SerialName("secondary_keys") val secondaryKeys: List<String> = emptyList(),
        val content: String = "",
        val priority: Int? = null,
        val enabled: Boolean = true,
        val constant: Boolean = false,
        val selective: Boolean = false,
        @SerialName("case_sensitive") val caseSensitive: Boolean = false,
        val comment: String = "",
    )

    /** 内部卡数据映射为公开领域模型。 */
    private fun CardData.toCharacterCard(): CharacterCard = CharacterCard(
        name = name,
        description = description,
        personality = personality,
        scenario = scenario,
        firstMes = firstMes,
        mesExample = mesExample,
        systemPrompt = systemPrompt,
        postHistoryInstructions = postHistoryInstructions,
        creator = creator,
        characterVersion = characterVersion,
        alternateGreetings = alternateGreetings,
        worldBook = characterBook?.toWorldBook(),
    )

    /** 内部角色书映射为公开领域模型。 */
    private fun CharacterBookData.toWorldBook(): WorldBook = WorldBook(
        name = name,
        scanDepth = scanDepth,
        tokenBudget = tokenBudget,
        recursiveScanning = recursiveScanning,
        entries = entries.map { entry -> entry.toWorldBookEntry() },
    )

    /** 内部条目映射为公开领域模型。 */
    private fun BookEntryData.toWorldBookEntry(): WorldBookEntry = WorldBookEntry(
        keys = keys,
        secondaryKeys = secondaryKeys,
        content = content,
        priority = priority,
        enabled = enabled,
        constant = constant,
        selective = selective,
        caseSensitive = caseSensitive,
        comment = comment,
    )
}
