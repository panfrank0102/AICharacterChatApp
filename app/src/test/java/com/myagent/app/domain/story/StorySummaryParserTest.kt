package com.myagent.app.domain.story

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [StorySummaryParser] 的单元测试。
 */
class StorySummaryParserTest {

    @Test
    fun parse_noBranches_returnsTrimmedText() {
        val result = StorySummaryParser.parse("  主角抵达了城镇。  ", expectBranches = false)

        assertEquals("主角抵达了城镇。", result?.summary)
        assertEquals(emptyList<String>(), result?.branches)
    }

    @Test
    fun parse_noBranches_stripsSummaryPrefix() {
        val result = StorySummaryParser.parse("总结：主角抵达了城镇。", expectBranches = false)

        assertEquals("主角抵达了城镇。", result?.summary)
    }

    @Test
    fun parse_noBranches_stripsCodeFences() {
        val result = StorySummaryParser.parse("```\n主角抵达了城镇。\n```", expectBranches = false)

        assertEquals("主角抵达了城镇。", result?.summary)
    }

    @Test
    fun parse_blank_returnsNull() {
        assertNull(StorySummaryParser.parse("   ", expectBranches = false))
        assertNull(StorySummaryParser.parse("```\n```", expectBranches = true))
    }

    @Test
    fun parse_branches_parsesAllFour() {
        val reply = "总结：主角进入城堡\n选项1：敲门\n选项2：翻墙\n选项3：离开\n选项4：大喊"
        val result = StorySummaryParser.parse(reply, expectBranches = true)

        assertEquals("主角进入城堡", result?.summary)
        assertEquals(listOf("敲门", "翻墙", "离开", "大喊"), result?.branches)
    }

    @Test
    fun parse_branches_partialOptions_keepsFoundOnes() {
        val reply = "总结：主角离开\n选项1：向东\n选项3：向西"
        val result = StorySummaryParser.parse(reply, expectBranches = true)

        assertEquals("主角离开", result?.summary)
        assertEquals(listOf("向东", "向西"), result?.branches)
    }

    @Test
    fun parse_branches_modelIgnoresFormat_wholeTextAsSummary() {
        val result = StorySummaryParser.parse("主角直接走进了森林。", expectBranches = true)

        assertEquals("主角直接走进了森林。", result?.summary)
        assertEquals(emptyList<String>(), result?.branches)
    }

    @Test
    fun parse_branches_optionsWithoutSummary_returnsNull() {
        assertNull(StorySummaryParser.parse("选项1：向东\n选项2：向西", expectBranches = true))
    }
}
