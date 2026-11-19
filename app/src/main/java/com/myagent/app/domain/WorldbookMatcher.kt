package com.myagent.app.domain

/**
 * 世界书条目匹配器。
 *
 * 语义遵循需求文档 FR-5：
 * - 扫描窗口 = 本轮输入 + 最近 [scanCount] 条历史消息正文（[history] 按时间升序，取末尾若干条）；
 * - 条目任一非空关键词作为子串出现在扫描文本中即命中，匹配不区分大小写；
 * - 递归触发：本轮新命中条目的正文再次参与关键词匹配，最多递归 [maxDepth] 轮
 *   （[maxDepth] = 0 表示只有第 0 轮，不再递归）；
 * - 同一条目整次调用最多注入一次（按 id 去重）；
 * - 结果按 priority 降序排列，priority 相同时按 id 升序排列；
 * - 正文为空白（blank）的条目不注入，也不参与递归传播。
 */
object WorldbookMatcher {

    /**
     * 一条世界书条目。
     *
     * @property id 条目唯一标识（用于去重与同优先级排序）
     * @property keywords 关键词列表，任一命中即触发；空白关键词被忽略
     * @property content 命中后注入的正文
     * @property priority 优先级，数值越大排序越靠前
     */
    data class Entry(
        val id: Long,
        val keywords: List<String>,
        val content: String,
        val priority: Int,
    )

    /**
     * 计算本轮需要注入的世界书条目。
     *
     * @param input 本轮用户输入，恒参与扫描
     * @param history 历史消息正文，按时间升序排列
     * @param entries 世界书条目，顺序无关，算法自行处理
     * @param scanCount 扫描的历史消息条数；小于等于 0 表示世界书扫描关闭
     * @param maxDepth 递归轮数上限；0 表示不递归
     * @return 排序后的命中条目列表，可能为空
     */
    fun match(
        input: String,
        history: List<String>,
        entries: List<Entry>,
        scanCount: Int,
        maxDepth: Int,
    ): List<Entry> {
        // scanCount <= 0 表示世界书扫描关闭；没有条目时也无需扫描。
        if (scanCount <= 0 || entries.isEmpty()) return emptyList()

        // 正文为空白（blank）的条目不会注入，也不参与递归传播，直接排除在候选之外。
        val candidates = entries.filter { it.content.isNotBlank() }
        if (candidates.isEmpty()) return emptyList()

        val triggeredIds = LinkedHashSet<Long>()
        val matched = ArrayList<Entry>()
        val effectiveMaxDepth = maxDepth.coerceAtLeast(0)

        // 第 0 轮：扫描窗口 = 本轮输入 + 最近 scanCount 条历史消息。
        var scanText = buildString {
            append(input)
            history.takeLast(scanCount).forEach { message ->
                append('\n')
                append(message)
            }
        }

        var depth = 0
        while (true) {
            val lowerScanText = scanText.lowercase()
            val newlyMatched = ArrayList<Entry>()
            for (entry in candidates) {
                // 同一条目整次调用最多注入一次（按 id 去重，防自触发死循环）。
                if (entry.id in triggeredIds) continue
                if (!matches(entry, lowerScanText)) continue
                triggeredIds.add(entry.id)
                newlyMatched.add(entry)
                matched.add(entry)
            }

            if (depth >= effectiveMaxDepth || newlyMatched.isEmpty()) break

            // 下一轮只把"上一轮新命中条目"的正文作为扫描文本：
            // 已触发条目的正文在更早的轮次中已经扫描过，且不会再次参与匹配，
            // 因此增量扫描与"累积所有已注入正文"的扫描结果等价。
            scanText = newlyMatched.joinToString(separator = "\n") { it.content }
            depth++
        }

        return matched.sortedWith(
            compareByDescending<Entry> { it.priority }.thenBy { it.id },
        )
    }

    /**
     * 判断条目是否命中已转小写的扫描文本；空白关键词忽略，匹配不区分大小写。
     */
    private fun matches(entry: Entry, lowerScanText: String): Boolean =
        entry.keywords.any { keyword ->
            keyword.isNotBlank() && lowerScanText.contains(keyword.lowercase())
        }
}
