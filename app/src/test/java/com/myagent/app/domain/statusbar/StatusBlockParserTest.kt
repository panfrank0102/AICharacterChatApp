package com.myagent.app.domain.statusbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [StatusBlockParser] 的单元测试：覆盖静态解析、正文清理、流式状态栏渐进解析与提取校验。
 */
class StatusBlockParserTest {

    // ------------------------------------------------------------------
    // 静态解析（parse）
    // ------------------------------------------------------------------

    @Test
    fun parse_singleBlockWithTextAround() {
        val content = "开场白\n<StatusBlock>\n<details>\n<summary>时间</summary>\n```\n下午四点\n```\n</details>\n</StatusBlock>\n结束语"

        val blocks = StatusBlockParser.parse(content)

        assertEquals(
            listOf(
                ChatContentBlock.Text("开场白\n"),
                ChatContentBlock.StatusPanel(
                    listOf(StatusSection("时间", listOf(StatusPart.Code("下午四点")))),
                ),
                ChatContentBlock.Text("\n结束语"),
            ),
            blocks,
        )
    }

    @Test
    fun parse_twoStatusBlocks_returnsTwoPanels() {
        val content = "<StatusBlock><details><summary>一</summary>甲</details></StatusBlock>\n" +
            "<STATUSBLOCK><details><summary>二</summary>乙</DETAILS></STATUSBLOCK>"

        val blocks = StatusBlockParser.parse(content)

        assertEquals(2, blocks.size)
        val panels = blocks.map { it as ChatContentBlock.StatusPanel }
        assertEquals(listOf("一", "二"), panels.map { it.sections.single().title })
        assertEquals(
            listOf(listOf(StatusPart.Plain("甲")), listOf(StatusPart.Plain("乙"))),
            panels.map { it.sections.single().parts },
        )
    }

    @Test
    fun parse_unclosedStatusBlock_treatsWholeRestAsText() {
        val content = "<StatusBlock>\n<details>\n<summary>世界信息</summary>\n正文尚未输出完"

        val blocks = StatusBlockParser.parse(content)

        assertEquals(listOf(ChatContentBlock.Text(content)), blocks)
    }

    @Test
    fun parse_statusBlockWithoutDetails_treatsBlockAsText() {
        val content = "<StatusBlock><br>没有分区的状态栏</StatusBlock>"

        val blocks = StatusBlockParser.parse(content)

        assertEquals(listOf(ChatContentBlock.Text(content)), blocks)
    }

    @Test
    fun parse_multiSections_keepsPlainAndCodeOrder() {
        val content = "<StatusBlock>\n" +
            "<details>\n<summary>{{user}}</summary>\n身份说明\n```\n👤物种：人类\n```\n补充说明\n</details>\n" +
            "</StatusBlock>"

        val blocks = StatusBlockParser.parse(content)

        val section = (blocks.single() as ChatContentBlock.StatusPanel).sections.single()
        assertEquals("{{user}}", section.title)
        assertEquals(
            listOf(
                StatusPart.Plain("身份说明"),
                StatusPart.Code("👤物种：人类"),
                StatusPart.Plain("补充说明"),
            ),
            section.parts,
        )
    }

    @Test
    fun parse_missingSummary_emptyTitle() {
        val content = "<StatusBlock><details>没有标题的内容</details></StatusBlock>"

        val blocks = StatusBlockParser.parse(content)

        val section = (blocks.single() as ChatContentBlock.StatusPanel).sections.single()
        assertEquals("", section.title)
        assertEquals(listOf(StatusPart.Plain("没有标题的内容")), section.parts)
    }

    @Test
    fun parse_unclosedCodeFence_treatsRestAsCode() {
        val content = "<StatusBlock><details><summary>代码</summary>\n```\n第一行\n第二行</details></StatusBlock>"

        val blocks = StatusBlockParser.parse(content)

        val section = (blocks.single() as ChatContentBlock.StatusPanel).sections.single()
        assertEquals("代码", section.title)
        assertEquals(listOf(StatusPart.Code("第一行\n第二行")), section.parts)
    }

    @Test
    fun parse_mixedCaseTags_areRecognized() {
        val content = "<STATUSBLOCK><DeTaIlS><SuMmArY>标题</sUmMaRy>正文</DETAILS></STATUSBLOCK>"

        val blocks = StatusBlockParser.parse(content)

        val section = (blocks.single() as ChatContentBlock.StatusPanel).sections.single()
        assertEquals("标题", section.title)
        assertEquals(listOf(StatusPart.Plain("正文")), section.parts)
    }

    @Test
    fun parse_emptyContent_returnsEmptyList() {
        assertTrue(StatusBlockParser.parse("").isEmpty())
    }

    // ------------------------------------------------------------------
    // 正文清理（stripStatusBlocks）
    // ------------------------------------------------------------------

    @Test
    fun strip_removesCompleteBlocksKeepingOtherText() {
        val text = "正文开始<StatusBlock><details><summary>s</summary>```x```</details></StatusBlock>正文结束"

        assertEquals("正文开始正文结束", StatusBlockParser.stripStatusBlocks(text))
    }

    @Test
    fun strip_multipleBlocks() {
        val text = "甲<StatusBlock><details><summary>s</summary>x</details></StatusBlock>乙" +
            "<StatusBlock><details><summary>t</summary>y</details></StatusBlock>丙"

        assertEquals("甲乙丙", StatusBlockParser.stripStatusBlocks(text))
    }

    @Test
    fun strip_unclosedBlockDropsRest() {
        assertEquals("前面", StatusBlockParser.stripStatusBlocks("前面<StatusBlock><details>"))
    }

    @Test
    fun strip_trailingPartialTagRemoved() {
        assertEquals("前面", StatusBlockParser.stripStatusBlocks("前面<Stat"))
        assertEquals("abc", StatusBlockParser.stripStatusBlocks("abc<"))
    }

    @Test
    fun strip_noBlock_trimsWhitespace() {
        assertEquals("hello", StatusBlockParser.stripStatusBlocks("  hello  "))
    }

    // ------------------------------------------------------------------
    // 流式状态栏渐进解析（parseStreamingStatus）
    // ------------------------------------------------------------------

    @Test
    fun streaming_beforeOpenTag_returnsEmpty() {
        assertTrue(StatusBlockParser.parseStreamingStatus("").isEmpty())
        assertTrue(StatusBlockParser.parseStreamingStatus("还没有标签").isEmpty())
    }

    @Test
    fun streaming_openTagOnly_createsEmptyPanel() {
        // 收到开标签即应能进入"面板已创建"状态（分区为空）。
        val sections = StatusBlockParser.parseStreamingStatus("<StatusBlock>")

        assertTrue(sections.isEmpty())
        assertTrue(StatusBlockParser.isStatusBlockStarted("<StatusBlock>"))
    }

    @Test
    fun streaming_partialSection_fillsProgressively() {
        val raw = "<StatusBlock>\n<details>\n<summary>世界信息</summary>\n```\n☁️天空：晴朗"

        val sections = StatusBlockParser.parseStreamingStatus(raw)

        val section = sections.single()
        assertEquals("世界信息", section.title)
        // 围栏未闭合：内容按代码展示。
        assertEquals(listOf(StatusPart.Code("☁️天空：晴朗")), section.parts)
    }

    @Test
    fun streaming_closingTagIsNotDisplayed() {
        val raw = "<StatusBlock>\n<details>\n<summary>时间</summary>\n```\n下午\n```\n</details>\n</StatusBlock>"

        val sections = StatusBlockParser.parseStreamingStatus(raw)

        val section = sections.single()
        assertEquals("时间", section.title)
        assertEquals(listOf(StatusPart.Code("下午")), section.parts)
    }

    @Test
    fun streaming_unclosedSummary_becomesContent() {
        val raw = "<StatusBlock><details><summary>世界"

        val section = StatusBlockParser.parseStreamingStatus(raw).single()

        assertEquals("", section.title)
        assertEquals(listOf(StatusPart.Plain("世界")), section.parts)
    }

    // ------------------------------------------------------------------
    // 状态栏回复校验提取（extractStatusBlock / buildPartialStatusBlock）
    // ------------------------------------------------------------------

    @Test
    fun extract_validBlock_normalizesAndStripsFences() {
        val reply = "好的，以下是更新后的状态栏：\n```html\n<StatusBlock>\n" +
            "<details><summary>世界信息</summary>\n```\n☁️天空：晴朗\n```\n</details>\n" +
            "</StatusBlock>\n```\n以上。"

        val block = StatusBlockParser.extractStatusBlock(reply)

        assertTrue(block != null)
        assertTrue(block!!.startsWith("<StatusBlock>"))
        assertTrue(block.endsWith("</StatusBlock>"))
        assertTrue(block.contains("<details>"))
        assertFalse("外层代码围栏应被剥离", block.contains("```html"))
    }

    @Test
    fun extract_noBlock_returnsNull() {
        assertNull(StatusBlockParser.extractStatusBlock("没有状态栏的普通回复"))
    }

    @Test
    fun extract_blockWithoutDetails_returnsNull() {
        assertNull(StatusBlockParser.extractStatusBlock("<StatusBlock></StatusBlock>"))
    }

    @Test
    fun buildPartial_validPartial_autoCloses() {
        val partial = StatusBlockParser.buildPartialStatusBlock("<StatusBlock><details><summary>世")

        assertTrue(partial != null)
        assertTrue(partial!!.startsWith("<StatusBlock>"))
        assertTrue(partial.endsWith("</StatusBlock>"))
        assertTrue(partial.contains("<details>"))
    }

    @Test
    fun buildPartial_noRecognizableContent_returnsNull() {
        assertNull(StatusBlockParser.buildPartialStatusBlock("<StatusBlock>"))
        assertNull(StatusBlockParser.buildPartialStatusBlock("没有标签"))
    }
}
