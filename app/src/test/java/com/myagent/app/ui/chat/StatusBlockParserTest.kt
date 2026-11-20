package com.myagent.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * StatusBlockParser 的单元测试。
 *
 * 覆盖状态栏整体分块、分区拆分、围栏代码块、未闭合输入以及标签大小写不敏感等规则。
 */
class StatusBlockParserTest {

    @Test
    fun parse_singleSectionWithCode_returnsStatusPanelWithoutEmptyText() {
        val content = """
            <StatusBlock>
            <details>
            <summary>世界信息</summary>
            ```
            ☁️天空：晴朗，有几缕薄云
            ⏰时间: 下午4点左右
            ```
            </details>
            </StatusBlock>
        """.trimIndent()

        val blocks = StatusBlockParser.parse(content)

        assertEquals(1, blocks.size)
        assertTrue(blocks.single() is ChatContentBlock.StatusPanel)
        val panel = blocks.single() as ChatContentBlock.StatusPanel
        assertEquals(1, panel.sections.size)
        val section = panel.sections.single()
        assertEquals("世界信息", section.title)
        assertEquals(
            listOf(StatusPart.Code("☁️天空：晴朗，有几缕薄云\n⏰时间: 下午4点左右")),
            section.parts,
        )
    }

    @Test
    fun parse_textAroundStatusBlock_keepsTextPanelTextOrder() {
        val content = """
            开场白
            <StatusBlock>
            <details>
            <summary>时间</summary>
            ```
            下午四点
            ```
            </details>
            </StatusBlock>
            结束语
        """.trimIndent()

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
        val content = """
            <StatusBlock>
            <details>
            <summary>{{user}}</summary>
            身份说明
            ```
            👤物种：人类
            💳身份：冒险者
            ```
            补充说明
            </details>
            <details>
            <summary>世界信息</summary>
            ```
            晴朗
            ```
            </details>
            </StatusBlock>
        """.trimIndent()

        val blocks = StatusBlockParser.parse(content)

        val panel = blocks.single() as ChatContentBlock.StatusPanel
        assertEquals(2, panel.sections.size)
        val userSection = panel.sections[0]
        assertEquals("{{user}}", userSection.title)
        assertEquals(
            listOf(
                StatusPart.Plain("身份说明"),
                StatusPart.Code("👤物种：人类\n💳身份：冒险者"),
                StatusPart.Plain("补充说明"),
            ),
            userSection.parts,
        )
        assertEquals("世界信息", panel.sections[1].title)
        assertEquals(listOf(StatusPart.Code("晴朗")), panel.sections[1].parts)
    }

    @Test
    fun parse_sectionWithoutSummary_hasEmptyTitle() {
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
}
