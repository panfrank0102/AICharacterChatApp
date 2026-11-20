package com.myagent.app.data.repository

import com.myagent.app.domain.import.CharacterCard

/**
 * 角色卡导入器：把一张已解析完成的角色卡一次性落库为完整会话。
 *
 * 导入过程按固定顺序执行：
 * 1. 新建会话，标题取角色名，会话提示词由卡片的文字设定拼接而成；
 * 2. 把卡片附带世界书的条目逐条写入新会话的世界书，没有关键词的条目跳过；
 * 3. 按用户勾选插入备用开场白（AI 首条消息），最后按需插入自定义开场（用户消息）。
 *
 * 会话的扫描条数与递归深度继承角色书声明，未声明时使用需求文档中的默认值。
 *
 * @param conversationRepository 会话仓库
 * @param worldbookRepository 世界书仓库
 * @param messageRepository 消息仓库
 */
class CharacterCardImporter(
    private val conversationRepository: ConversationRepository,
    private val worldbookRepository: WorldbookRepository,
    private val messageRepository: MessageRepository,
) {

    /**
     * 导入选项。
     *
     * @property card 待导入的角色卡
     * @property selectedGreetingIndexes 选中的备用开场白下标（从 0 开始）
     * @property customOpening 自定义开场白，将作为第一条用户消息插入
     */
    data class Options(
        val card: CharacterCard,
        val selectedGreetingIndexes: Set<Int> = emptySet(),
        val customOpening: String = "",
    )

    /**
     * 导入结果。
     *
     * @property conversationId 新建的会话 id
     * @property importedWorldbookCount 成功写入的世界书条目数
     * @property skippedWorldbookCount 因为没有关键词而跳过的世界书条目数
     */
    data class Result(
        val conversationId: Long,
        val importedWorldbookCount: Int,
        val skippedWorldbookCount: Int,
    )

    /**
     * 执行导入。
     *
     * @param options 导入选项
     * @return 导入结果，含新会话 id 与世界书条目的导入/跳过数量
     */
    suspend fun import(options: Options): Result {
        val card = options.card
        val title = card.name.trim().ifBlank { FALLBACK_TITLE }
        val book = card.worldBook

        // 角色书声明的扫描条数优先，超出 0..200 时收敛到边界。
        val scanCount = (book?.scanDepth ?: DEFAULT_SCAN_COUNT).coerceIn(MIN_SCAN_COUNT, MAX_SCAN_COUNT)
        // 角色书显式关闭递归扫描时深度为 0，否则使用默认递归深度。
        val recursionDepth = if (book?.recursiveScanning == false) 0 else DEFAULT_RECURSION_DEPTH

        val conversationId = conversationRepository.create(
            title = title,
            presetId = null,
            systemPrompt = buildSystemPrompt(card),
            scanCount = scanCount,
            recursionDepth = recursionDepth,
            userName = DEFAULT_USER_NAME,
        )

        var importedCount = 0
        var skippedCount = 0
        book?.entries?.forEach { entry ->
            val keywords = splitKeywords(entry.keys, entry.secondaryKeys)
            if (keywords.isEmpty()) {
                // 没有关键词的条目永远不会被命中，直接跳过。
                skippedCount++
                return@forEach
            }
            worldbookRepository.save(
                conversationId = conversationId,
                title = entry.comment.trim().ifBlank { "条目 ${importedCount + 1}" },
                keywords = keywords,
                content = entry.content.replace("{{char}}", card.name),
                priority = entry.priority ?: DEFAULT_ENTRY_PRIORITY,
                enabled = entry.enabled,
            )
            importedCount++
        }

        card.alternateGreetings.forEachIndexed { index, greeting ->
            if (index in options.selectedGreetingIndexes) {
                messageRepository.insertAssistant(conversationId, greeting.replace("{{char}}", card.name))
            }
        }

        val customOpening = options.customOpening.trim()
        if (customOpening.isNotEmpty()) {
            messageRepository.insertUser(conversationId, customOpening)
        }

        return Result(
            conversationId = conversationId,
            importedWorldbookCount = importedCount,
            skippedWorldbookCount = skippedCount,
        )
    }

    companion object {

        /** 角色书未声明扫描深度时使用的扫描条数。 */
        private const val DEFAULT_SCAN_COUNT = 20

        /** 扫描条数下限，0 表示关闭世界书扫描。 */
        private const val MIN_SCAN_COUNT = 0

        /** 扫描条数上限。 */
        private const val MAX_SCAN_COUNT = 200

        /** 角色书未声明递归扫描时使用的递归深度。 */
        private const val DEFAULT_RECURSION_DEPTH = 2

        /** 世界书条目的默认优先级。 */
        private const val DEFAULT_ENTRY_PRIORITY = 10

        /** 新会话默认的用户称呼。 */
        private const val DEFAULT_USER_NAME = "用户"

        /** 角色卡没有名称时使用的会话标题。 */
        private const val FALLBACK_TITLE = "新会话"

        /**
         * 拼接会话提示词。
         *
         * 按 systemPrompt → description → personality → scenario → mesExample 的顺序拼接，
         * 空字段跳过（各段之间以空行分隔），并把其中的 `{{char}}` 替换为角色卡名。
         * 开场白 `firstMes` 不进入提示词。
         */
        fun buildSystemPrompt(card: CharacterCard): String {
            return listOf(
                card.systemPrompt,
                card.description,
                card.personality,
                card.scenario,
                card.mesExample,
            ).filter { section -> section.isNotBlank() }
                .joinToString("\n\n")
                .replace("{{char}}", card.name)
        }

        /**
         * 合并多组关键词并拆分。
         *
         * 每一项先按 `|` 再拆分一次，随后去除首尾空白、丢弃空白项；
         * 结果去重并保持原有顺序。
         */
        fun splitKeywords(vararg keyLists: List<String>): List<String> {
            val merged = LinkedHashSet<String>()
            keyLists.forEach { keys ->
                keys.forEach { raw ->
                    raw.split('|').forEach { part ->
                        val keyword = part.trim()
                        if (keyword.isNotEmpty()) {
                            merged.add(keyword)
                        }
                    }
                }
            }
            return merged.toList()
        }
    }
}
