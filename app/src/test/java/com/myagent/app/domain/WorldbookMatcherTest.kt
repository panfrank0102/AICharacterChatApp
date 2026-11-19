package com.myagent.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * WorldbookMatcher 的单元测试。
 */
class WorldbookMatcherTest {

    @Test
    fun match_isCaseInsensitive() {
        val entries = listOf(entry(1, listOf("DrAgOn"), "龙"))
        assertEquals(
            listOf(1L),
            matchIds(input = "The DRAGON is here", entries = entries),
        )
    }

    @Test
    fun match_triggersWhenAnyKeywordMatches() {
        val entries = listOf(entry(1, listOf("sword", "shield"), "装备"))
        assertEquals(
            listOf(1L),
            matchIds(input = "I raise my SHIELD", entries = entries),
        )
    }

    @Test
    fun match_scansOnlyInputAndRecentHistory() {
        val entries = listOf(entry(1, listOf("dragon"), "龙"))
        val history = listOf("ancient dragon", "hello", "hi")
        // scanCount=2 只覆盖最近两条 "hello" 与 "hi"，更早的 "ancient dragon" 不参与。
        assertEquals(
            emptyList<Long>(),
            matchIds(input = "nothing", history = history, entries = entries, scanCount = 2),
        )
        // 同一批历史下 scanCount=3，更早的 "ancient dragon" 才进入扫描窗口。
        assertEquals(
            listOf(1L),
            matchIds(input = "nothing", history = history, entries = entries, scanCount = 3),
        )
        // 本轮输入恒参与扫描，与历史条数无关。
        assertEquals(
            listOf(1L),
            matchIds(input = "dragon", history = listOf("nothing"), entries = entries, scanCount = 1),
        )
    }

    @Test
    fun match_returnsEmptyWhenScanCountIsZero() {
        val entries = listOf(entry(1, listOf("dragon"), "龙"))
        assertEquals(
            emptyList<Long>(),
            matchIds(
                input = "dragon",
                history = listOf("dragon"),
                entries = entries,
                scanCount = 0,
            ),
        )
    }

    @Test
    fun match_sortsByPriorityDescendingThenIdAscending() {
        val entries = listOf(
            entry(4, listOf("dragon"), "P5-小", priority = 5),
            entry(1, listOf("dragon"), "P10-小", priority = 10),
            entry(3, listOf("dragon"), "P5-大", priority = 5),
            entry(2, listOf("dragon"), "P10-大", priority = 10),
        )
        // 优先级降序；同优先级时 id 升序（与输入顺序无关）。
        assertEquals(
            listOf(1L, 2L, 3L, 4L),
            matchIds(input = "dragon", entries = entries),
        )
    }

    @Test
    fun match_doesNotRecurseWhenMaxDepthIsZero() {
        val entries = listOf(
            entry(1, listOf("dragon"), "龙会喷火"),
            entry(2, listOf("喷火"), "火焰伤害很高"),
        )
        // 第 0 轮只命中 id=1；maxDepth=0 不再用其正文递归，故不会命中 id=2。
        assertEquals(
            listOf(1L),
            matchIds(input = "dragon", entries = entries, maxDepth = 0),
        )
        // maxDepth=1 时第 1 轮扫描 id=1 的正文 "龙会喷火"，从而命中 id=2。
        assertEquals(
            listOf(1L, 2L),
            matchIds(input = "dragon", entries = entries, maxDepth = 1),
        )
    }

    @Test
    fun match_deduplicatesEntriesMatchedAgainDuringRecursion() {
        val entries = listOf(
            entry(1, listOf("dragon"), "dragon fire"),
            entry(2, listOf("fire"), "a dragon appears"),
        )
        // 第 0 轮命中 1；第 1 轮从 1 的正文命中 2；第 2 轮 2 的正文再次提到 dragon，
        // 但 1 已触发，按 id 去重后只出现一次。
        assertEquals(
            listOf(1L, 2L),
            matchIds(input = "dragon", entries = entries, maxDepth = 5),
        )
    }

    @Test
    fun match_skipsBlankContentEntries() {
        val entries = listOf(
            entry(1, listOf("dragon"), ""),
            entry(2, listOf("dragon"), "   \n\t"),
        )
        assertEquals(
            emptyList<Long>(),
            matchIds(input = "dragon", entries = entries, maxDepth = 2),
        )
    }

    @Test
    fun match_ignoresBlankKeywords() {
        val onlyBlankKeywords = listOf(entry(1, listOf("", "   "), "龙"))
        assertEquals(
            emptyList<Long>(),
            matchIds(input = "anything", entries = onlyBlankKeywords),
        )
        val mixedKeywords = listOf(entry(2, listOf("", "dragon"), "龙"))
        assertEquals(
            listOf(2L),
            matchIds(input = "dragon", entries = mixedKeywords),
        )
    }

    private fun entry(
        id: Long,
        keywords: List<String>,
        content: String,
        priority: Int = 0,
    ): WorldbookMatcher.Entry = WorldbookMatcher.Entry(
        id = id,
        keywords = keywords,
        content = content,
        priority = priority,
    )

    private fun matchIds(
        input: String,
        history: List<String> = emptyList(),
        entries: List<WorldbookMatcher.Entry>,
        scanCount: Int = 20,
        maxDepth: Int = 0,
    ): List<Long> = WorldbookMatcher.match(
        input = input,
        history = history,
        entries = entries,
        scanCount = scanCount,
        maxDepth = maxDepth,
    ).map { it.id }
}
