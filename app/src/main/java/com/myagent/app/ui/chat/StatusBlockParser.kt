package com.myagent.app.ui.chat

/**
 * 聊天消息中的一种显示块。
 *
 * 仅用于聊天界面展示层：发送给模型的请求内容和写入数据库的内容始终保持原文，
 * 本类型只描述「原文在 UI 上应该如何被拆分显示」。
 */
internal sealed interface ChatContentBlock {
    /** 普通文本块，即状态栏之外的原文内容（未识别标签也保留在文本中）。 */
    data class Text(val text: String) : ChatContentBlock

    /** 状态栏面板块，由若干可折叠分区组成。 */
    data class StatusPanel(val sections: List<StatusSection>) : ChatContentBlock
}

/**
 * 状态栏面板中的一个分区，对应一段 `<details>…</details>` 内容。
 *
 * @param title 分区标题，取自 `<summary>` 文本；缺失时为空串。
 * @param parts 分区正文，按围栏代码块拆分为普通文本与代码。
 */
internal data class StatusSection(val title: String, val parts: List<StatusPart>)

/** 状态栏分区中的一段正文内容。 */
internal sealed interface StatusPart {
    /** 围栏代码块之外的普通文本。 */
    data class Plain(val text: String) : StatusPart

    /** 围栏代码块中的内容，围栏行本身不包含在内。 */
    data class Code(val text: String) : StatusPart
}

/**
 * 解析聊天消息里的 `<StatusBlock>` 状态栏标记。
 *
 * 仅供显示层使用，原文不做任何修改。解析规则：
 * 1. 标签名大小写不敏感（`<StatusBlock>`、`<statusblock>` 均可识别）；
 * 2. 按出现顺序扫描，`<StatusBlock>` 与对应 `</StatusBlock>` 之间的内容解析为面板，
 *    块外文本保持原样输出为普通文本块；
 * 3. 未闭合的 `<StatusBlock>`（例如流式输出途中）整体按普通文本处理，不吞内容；
 * 4. 块内按 `<details>` 拆分分区，`<summary>` 文本作为标题，summary 之后的内容
 *    按围栏代码块拆分为 [StatusPart.Code] 与 [StatusPart.Plain]；
 * 5. 块内没有任何 `<details>` 分区时，整个状态栏标记按普通文本处理，避免空面板；
 * 6. 未知标签（如 `<br>`）保留在普通文本中，`<details>` / `<summary>` 标签本身不显示。
 */
internal object StatusBlockParser {

    /** 围栏代码块的分隔符。 */
    private const val FENCE = "```"

    private val statusBlockOpen = Regex("<statusblock(?:\\s[^>]*)?>", RegexOption.IGNORE_CASE)
    private val statusBlockClose = Regex("</statusblock\\s*>", RegexOption.IGNORE_CASE)
    private val detailsOpen = Regex("<details(?:\\s[^>]*)?>", RegexOption.IGNORE_CASE)
    private val detailsClose = Regex("</details\\s*>", RegexOption.IGNORE_CASE)
    private val summaryOpen = Regex("<summary(?:\\s[^>]*)?>", RegexOption.IGNORE_CASE)
    private val summaryClose = Regex("</summary\\s*>", RegexOption.IGNORE_CASE)

    /**
     * 把消息原文解析为显示块列表。
     *
     * @param content 消息原文。
     * @return 按出现顺序排列的显示块；空字符串返回空列表。
     */
    fun parse(content: String): List<ChatContentBlock> {
        if (content.isEmpty()) return emptyList()

        val blocks = mutableListOf<ChatContentBlock>()
        var cursor = 0
        while (cursor < content.length) {
            val open = statusBlockOpen.find(content, cursor) ?: break
            val close = statusBlockClose.find(content, open.range.last + 1)

            // 状态栏之前的普通文本（空白文本不会生成块）。
            appendText(blocks, content.substring(cursor, open.range.first))

            if (close == null) {
                // 规则 3：未闭合的状态栏整体按普通文本处理，保留原文标记且不吞内容。
                appendText(blocks, content.substring(open.range.first))
                return blocks
            }

            val inner = content.substring(open.range.last + 1, close.range.first)
            val sections = parseSections(inner)
            if (sections.isEmpty()) {
                // 规则 5：没有解析出分区时，整个标记按普通文本处理，避免空面板并保留原文。
                appendText(blocks, content.substring(open.range.first, close.range.last + 1))
            } else {
                blocks += ChatContentBlock.StatusPanel(sections)
            }
            cursor = close.range.last + 1
        }

        // 最后一个状态栏之后的普通文本。
        appendText(blocks, content.substring(cursor))
        return blocks
    }

    /**
     * 追加普通文本块。
     *
     * 空白文本不生成块；相邻的普通文本会合并为一个块，保证原文顺序与内容不丢。
     *
     * @param blocks 已解析出的显示块列表。
     * @param text 待追加的文本。
     */
    private fun appendText(blocks: MutableList<ChatContentBlock>, text: String) {
        if (text.isBlank()) return
        val last = blocks.lastOrNull()
        if (last is ChatContentBlock.Text) {
            blocks[blocks.lastIndex] = ChatContentBlock.Text(last.text + text)
        } else {
            blocks += ChatContentBlock.Text(text)
        }
    }

    /**
     * 解析状态栏内部的分区列表。
     *
     * @param inner 一对 `<StatusBlock>` 标签之间的原文。
     * @return 分区列表；没有 `<details>` 时返回空列表。
     */
    private fun parseSections(inner: String): List<StatusSection> {
        val sections = mutableListOf<StatusSection>()
        var cursor = 0
        while (cursor < inner.length) {
            val open = detailsOpen.find(inner, cursor) ?: break
            val close = detailsClose.find(inner, open.range.last + 1)
            if (close == null) {
                // `<details>` 未闭合时把其后的内容当作一个分区，避免丢失内容。
                sections += parseSection(inner.substring(open.range.last + 1))
                break
            }
            sections += parseSection(inner.substring(open.range.last + 1, close.range.first))
            cursor = close.range.last + 1
        }
        return sections
    }

    /**
     * 解析单个分区的标题与正文。
     *
     * @param content 一对 `<details>` 标签之间的原文。
     * @return 分区数据；缺失或未闭合的 `<summary>` 会让标题为空串。
     */
    private fun parseSection(content: String): StatusSection {
        val summaryStart = summaryOpen.find(content)
        if (summaryStart == null) {
            return StatusSection(title = "", parts = parseParts(content))
        }
        val summaryEnd = summaryClose.find(content, summaryStart.range.last + 1)
        if (summaryEnd == null) {
            // `<summary>` 未闭合：去掉起始标签，其余内容按正文处理。
            val body = content.substring(0, summaryStart.range.first) +
                content.substring(summaryStart.range.last + 1)
            return StatusSection(title = "", parts = parseParts(body))
        }
        val title = content.substring(summaryStart.range.last + 1, summaryEnd.range.first).trim()
        // summary 之前若有文字也并入正文，避免丢失内容。
        val body = content.substring(0, summaryStart.range.first) +
            content.substring(summaryEnd.range.last + 1)
        return StatusSection(title = title, parts = parseParts(body))
    }

    /**
     * 按 ``` 围栏把正文拆分为普通文本与代码块。
     *
     * 每段内容 trim 后丢弃空白段；围栏未闭合时其后内容全部按代码处理。
     *
     * @param content 分区正文。
     * @return 按出现顺序排列的普通文本与代码块。
     */
    private fun parseParts(content: String): List<StatusPart> {
        val parts = mutableListOf<StatusPart>()
        val plainLines = mutableListOf<String>()
        val codeLines = mutableListOf<String>()
        var inCode = false

        fun flushPlain() {
            val text = plainLines.joinToString("\n").trim()
            if (text.isNotEmpty()) parts += StatusPart.Plain(text)
            plainLines.clear()
        }

        fun flushCode() {
            val text = codeLines.joinToString("\n").trim()
            if (text.isNotEmpty()) parts += StatusPart.Code(text)
            codeLines.clear()
        }

        content.split('\n').forEach { rawLine ->
            val line = rawLine.removeSuffix("\r")
            if (line.trim().startsWith(FENCE)) {
                // 围栏行本身不显示，只用于切换普通文本与代码块。
                if (inCode) flushCode() else flushPlain()
                inCode = !inCode
            } else if (inCode) {
                codeLines += line
            } else {
                plainLines += line
            }
        }

        if (inCode) {
            // 规则：围栏未闭合时，其后内容全部按代码处理。
            flushCode()
        } else {
            flushPlain()
        }
        return parts
    }
}
