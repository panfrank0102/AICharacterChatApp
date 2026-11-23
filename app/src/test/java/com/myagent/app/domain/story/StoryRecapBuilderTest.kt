package com.myagent.app.domain.story

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [StoryRecapBuilder] 的单元测试。
 */
class StoryRecapBuilderTest {

    private fun msg(role: String, content: String, summary: String = "") =
        StoryMessage(role = role, content = content, summary = summary)

    @Test
    fun build_enhancementOff_windowIsEverything() {
        val messages = listOf(
            msg("user", "问1"),
            msg("assistant", "答1", "总结1"),
        )
        val result = StoryRecapBuilder.build(messages, rounds = 1, enhancementEnabled = false)

        assertEquals("", result.recap)
        assertEquals(messages, result.window)
    }

    @Test
    fun build_emptyMessages() {
        val result = StoryRecapBuilder.build(emptyList(), rounds = 2, enhancementEnabled = true)

        assertEquals("", result.recap)
        assertEquals(emptyList<StoryMessage>(), result.window)
    }

    @Test
    fun build_noAssistantMessages_windowIsEverything() {
        val messages = listOf(msg("user", "问1"))
        val result = StoryRecapBuilder.build(messages, rounds = 1, enhancementEnabled = true)

        assertEquals("", result.recap)
        assertEquals(messages, result.window)
    }

    @Test
    fun build_rounds1_keepsLastAssistantOnly() {
        val messages = listOf(
            msg("user", "第一问"),
            msg("assistant", "第一答", "第一答总结"),
            msg("user", "第二问"),
            msg("assistant", "第二答", "第二答总结"),
        )
        val result = StoryRecapBuilder.build(messages, rounds = 1, enhancementEnabled = true)

        assertEquals("第一答总结", result.recap)
        assertEquals(listOf(msg("assistant", "第二答", "第二答总结")), result.window)
    }

    @Test
    fun build_rounds2_keepsTwoAssistantsAndUserBetween() {
        val messages = listOf(
            msg("user", "第一问"),
            msg("assistant", "第一答", "第一答总结"),
            msg("user", "第二问"),
            msg("assistant", "第二答", "第二答总结"),
            msg("user", "第三问"),
            msg("assistant", "第三答", "第三答总结"),
        )
        val result = StoryRecapBuilder.build(messages, rounds = 2, enhancementEnabled = true)

        // N=2：最近 2 条 AI 回复 + 夹在它们之间的 1 条用户输入。
        assertEquals("第一答总结", result.recap)
        assertEquals(
            listOf(
                msg("assistant", "第二答", "第二答总结"),
                msg("user", "第三问"),
                msg("assistant", "第三答", "第三答总结"),
            ),
            result.window,
        )
    }

    @Test
    fun build_recapSkipsEmptySummaries() {
        val messages = listOf(
            msg("user", "第一问"),
            msg("assistant", "第一答", ""),
            msg("user", "第二问"),
            msg("assistant", "第二答", "第二答总结"),
            msg("user", "第三问"),
            msg("assistant", "第三答", "第三答总结"),
        )
        val result = StoryRecapBuilder.build(messages, rounds = 1, enhancementEnabled = true)

        // 超窗的 AI 回复中，第一答没有总结 → 跳过；第二答的总结保留。
        assertEquals("第二答总结", result.recap)
        assertEquals(listOf(msg("assistant", "第三答", "第三答总结")), result.window)
    }

    @Test
    fun build_recapJoinsInOrder() {
        val messages = listOf(
            msg("user", "问1"),
            msg("assistant", "答1", "总结一"),
            msg("user", "问2"),
            msg("assistant", "答2", "总结二"),
            msg("user", "问3"),
            msg("assistant", "答3", "总结三"),
        )
        val result = StoryRecapBuilder.build(messages, rounds = 1, enhancementEnabled = true)

        assertEquals("总结一\n总结二", result.recap)
        assertEquals(listOf(msg("assistant", "答3", "总结三")), result.window)
    }
}
