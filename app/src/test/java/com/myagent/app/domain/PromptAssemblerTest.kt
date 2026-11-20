package com.myagent.app.domain

import com.myagent.app.network.dto.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PromptAssembler 的单元测试。
 */
class PromptAssemblerTest {

    @Test
    fun assemble_ordersMessagesAsRequired() {
        val globalPrompt = "全局提示词"
        val sessionPrompt = "会话提示词"
        val worldbookEntries = listOf(
            entry(id = 1, content = "世界书一", priority = 10),
            entry(id = 2, content = "世界书二", priority = 5),
        )
        val history = listOf(
            message("user", "历史用户"),
            message("assistant", "历史回复"),
        )
        val result = PromptAssembler.assemble(
            globalPrompt = globalPrompt,
            sessionPrompt = sessionPrompt,
            worldbookEntries = worldbookEntries,
            history = history,
            userInput = "本轮输入",
            contextLimitChars = 100_000,
        )
        assertEquals(
            listOf(
                message("system", globalPrompt),
                message("system", sessionPrompt),
                message("system", "世界书一"),
                message("system", "世界书二"),
                message("user", "历史用户"),
                message("assistant", "历史回复"),
                message("user", "本轮输入"),
            ),
            result,
        )
    }

    @Test
    fun assemble_skipsBlankPrompts() {
        val result = PromptAssembler.assemble(
            globalPrompt = "   ",
            sessionPrompt = "",
            worldbookEntries = emptyList(),
            history = emptyList(),
            userInput = "你好",
            contextLimitChars = 100_000,
        )
        assertEquals(listOf(message("user", "你好")), result)
    }

    @Test
    fun assemble_trimsOldestHistoryFirst() {
        // 固定部分只有 userInput（1 字符），两条历史各 3 字符，总 7 字符。
        // 预算 4：裁掉最旧一条 "aaa" 后剩 1 + 3 = 4，恰好不超限。
        val history = listOf(
            message("user", "aaa"),
            message("assistant", "bbb"),
        )
        val result = PromptAssembler.assemble(
            globalPrompt = "",
            sessionPrompt = "",
            worldbookEntries = emptyList(),
            history = history,
            userInput = "u",
            contextLimitChars = 4,
        )
        assertEquals(
            listOf(
                message("assistant", "bbb"),
                message("user", "u"),
            ),
            result,
        )
    }

    @Test
    fun assemble_dropsWorldbookFromTailAfterHistoryCleared() {
        // 固定部分只有 userInput（1 字符），两条世界书各 4 字符，一条历史 2 字符，总 11 字符。
        // 预算 6：先裁掉唯一的历史（剩 9）仍超限，再从尾部丢弃 id=2（低优先级）的世界书（剩 5）。
        val worldbookEntries = listOf(
            entry(id = 1, content = "xxxx", priority = 10),
            entry(id = 2, content = "yyyy", priority = 5),
        )
        val history = listOf(message("user", "hi"))
        val result = PromptAssembler.assemble(
            globalPrompt = "",
            sessionPrompt = "",
            worldbookEntries = worldbookEntries,
            history = history,
            userInput = "u",
            contextLimitChars = 6,
        )
        assertEquals(
            listOf(
                message("system", "xxxx"),
                message("user", "u"),
            ),
            result,
        )
    }

    @Test
    fun assemble_alwaysKeepsUserInput() {
        val userInput = "必须完整保留的本轮输入"
        val result = PromptAssembler.assemble(
            globalPrompt = "全局",
            sessionPrompt = "会话",
            worldbookEntries = listOf(entry(id = 1, content = "很长很长的世界书正文")),
            history = listOf(
                message("user", "旧消息"),
                message("assistant", "旧回复"),
            ),
            userInput = userInput,
            contextLimitChars = 0,
        )
        // 预算为 0 时历史与世界书都被裁掉，提示词与用户输入仍原样保留。
        assertEquals(
            listOf(
                message("system", "全局"),
                message("system", "会话"),
                message("user", userInput),
            ),
            result,
        )
        assertEquals("user", result.last().role)
        assertEquals(userInput, result.last().content)
    }

    @Test
    fun assemble_keepsEverythingWhenCharsEqualBudget() {
        // 总字符数 = 1(全局) + 2(世界书) + 3 + 4(历史) + 1(用户输入) = 11。
        val result = PromptAssembler.assemble(
            globalPrompt = "g",
            sessionPrompt = "",
            worldbookEntries = listOf(entry(id = 1, content = "ww")),
            history = listOf(
                message("user", "abc"),
                message("assistant", "defg"),
            ),
            userInput = "u",
            contextLimitChars = 11,
        )
        assertEquals(
            listOf("g", "ww", "abc", "defg", "u"),
            result.map { it.content },
        )
    }

    @Test
    fun assemble_placesPostHistoryPromptAsLastSystemMessage() {
        val result = PromptAssembler.assemble(
            globalPrompt = "全局",
            sessionPrompt = "会话",
            postHistoryPrompt = "后置指令",
            worldbookEntries = emptyList(),
            history = listOf(
                message("user", "旧问"),
                message("assistant", "旧答"),
            ),
            userInput = "新问",
            contextLimitChars = 100_000,
        )
        assertEquals(
            listOf(
                message("system", "全局"),
                message("system", "会话"),
                message("user", "旧问"),
                message("assistant", "旧答"),
                message("user", "新问"),
                message("system", "后置指令"),
            ),
            result,
        )
        assertEquals("system", result.last().role)
    }

    @Test
    fun assemble_skipsBlankPostHistoryPrompt() {
        val result = PromptAssembler.assemble(
            globalPrompt = "全局",
            sessionPrompt = "",
            postHistoryPrompt = "  ",
            worldbookEntries = emptyList(),
            history = emptyList(),
            userInput = "你好",
            contextLimitChars = 100_000,
        )
        assertEquals(listOf(message("system", "全局"), message("user", "你好")), result)
    }

    @Test
    fun assemble_trimsHistoryToFitPostHistoryPrompt() {
        // 固定部分 = 用户输入 1 + 后置提示词 2 = 3；两条历史各 3 字符，总 9。
        // 预算 6：裁掉最旧历史 "aaa" 后剩 3 + 3 = 6，恰好不超限。
        val result = PromptAssembler.assemble(
            globalPrompt = "",
            sessionPrompt = "",
            postHistoryPrompt = "pp",
            worldbookEntries = emptyList(),
            history = listOf(
                message("user", "aaa"),
                message("assistant", "bbb"),
            ),
            userInput = "u",
            contextLimitChars = 6,
        )
        assertEquals(listOf("bbb", "u", "pp"), result.map { it.content })
    }

    @Test
    fun assemble_keepsPostHistoryPromptWhenBudgetTooSmall() {
        val result = PromptAssembler.assemble(
            globalPrompt = "g",
            sessionPrompt = "",
            postHistoryPrompt = "pp",
            worldbookEntries = listOf(entry(id = 1, content = "ww")),
            history = listOf(message("user", "old")),
            userInput = "u",
            contextLimitChars = 0,
        )
        // 预算为 0 时历史与世界书都被裁掉，提示词与用户输入、后置提示词仍保留。
        assertEquals(listOf("g", "u", "pp"), result.map { it.content })
        assertEquals("system", result.last().role)
    }

    private fun entry(
        id: Long,
        content: String,
        priority: Int = 0,
    ): WorldbookMatcher.Entry = WorldbookMatcher.Entry(
        id = id,
        keywords = listOf("keyword-$id"),
        content = content,
        priority = priority,
    )

    private fun message(role: String, content: String): ChatMessage =
        ChatMessage(role = role, content = content)
}
