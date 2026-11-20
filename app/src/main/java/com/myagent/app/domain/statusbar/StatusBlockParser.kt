package com.myagent.app.domain.statusbar

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
 * 本对象同时服务于三类场景（均为纯函数，可单元测试）：
 * 1. 静态展示：把落库的完整消息解析为显示块（[parse]）；
 * 2. 状态栏优化·正文清理：从正文里剔除状态栏标记（[stripStatusBlocks]）；
 * 3. 状态栏优化·流式生成：边接收边解析状态栏、以及从回复中校验提取（[parseStreamingStatus]、
 *    [extractStatusBlock]、[buildPartialStatusBlock]）。
 *
 * 解析规则：
 * - 标签名大小写不敏感；
 * - 未闭合的 `<StatusBlock>` 在静态展示中按普通文本处理，不吞内容；
 * - 块内按 `<details>` 拆分分区，`<summary>` 文本作为标题，summary 之后的内容按围栏代码块拆分；
 * - 未知标签（如 `<br>`）保留在普通文本中，`<details>` / `<summary>` 标签本身不显示。
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

    /** 代码围栏行（含语言标记），用于提取时剥离。 */
    private val fenceLine = Regex("(?m)^\\s*```[^\\n]*$")

    /** 末尾不完整标签片段（如流式中途收到 "<Stat"）。 */
    private val trailingPartialTag = Regex("<[^>]*$")

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
                // 未闭合的状态栏整体按普通文本处理，保留原文标记且不吞内容。
                appendText(blocks, content.substring(open.range.first))
                return blocks
            }

            val inner = content.substring(open.range.last + 1, close.range.first)
            val sections = parseSections(inner)
            if (sections.isEmpty()) {
                // 没有解析出分区时，整个标记按普通文本处理，避免空面板并保留原文。
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
     * 从正文中剔除状态栏标记（状态栏优化功能使用）。
     *
     * 完整 `<StatusBlock>…</StatusBlock>` 块整体删除；未闭合的状态栏从开标签起、
     * 其后的内容全部丢弃（这些内容属于状态栏而非正文）；同时去掉流式中途残留的
     * 末尾不完整标签片段。结果做首尾空白裁剪。
     *
     * @param text 原始文本（可能仍在流式增量拼装中）。
     * @return 仅剩正文的文本。
     */
    fun stripStatusBlocks(text: String): String {
        if (text.isEmpty()) return ""
        val kept = StringBuilder()
        var cursor = 0
        while (cursor < text.length) {
            val open = statusBlockOpen.find(text, cursor) ?: break
            kept.append(text, cursor, open.range.first)
            val close = statusBlockClose.find(text, open.range.last + 1)
            if (close == null) {
                // 未闭合：丢弃其后全部内容（状态栏部分不属于正文）。
                return trailingPartialTag.replace(kept.toString(), "").trim()
            }
            cursor = close.range.last + 1
        }
        kept.append(text, cursor, text.length)
        return trailingPartialTag.replace(kept.toString(), "").trim()
    }

    /**
     * 判断流式文本中是否已经出现状态栏开标签（用于决定是否开始渲染状态栏面板）。
     */
    fun isStatusBlockStarted(text: String): Boolean = statusBlockOpen.containsMatchIn(text)

    /**
     * 解析「流式生成中」的状态栏内容，供渐进渲染使用。
     *
     * 忽略开标签之前的文字与 `<StatusBlock>` 标签本身（结束标签同样不参与显示），
     * 对尚未闭合的 `<details>` / `<summary>` / 围栏代码块做尽力解析：
     * 已出现的分区立即返回，内容随后续增量继续增长。
     *
     * @param raw 状态栏请求已接收的全部原文。
     * @return 已解析出的分区列表；尚未出现开标签时返回空列表。
     */
    fun parseStreamingStatus(raw: String): List<StatusSection> {
        val open = statusBlockOpen.find(raw) ?: return emptyList()
        val innerStart = open.range.last + 1
        val close = statusBlockClose.find(raw, innerStart)
        val body = if (close == null) {
            raw.substring(innerStart)
        } else {
            raw.substring(innerStart, close.range.first)
        }
        return parseSections(body)
    }

    /**
     * 从一次完整的状态栏请求回复中提取并校验 `<StatusBlock>…</StatusBlock>` 标记。
     *
     * 容忍回复被 ``` 代码围栏包裹或前后带有其他文字；要求至少包含一个 `<details>` 分区，
     * 否则视为无效（返回 null）。
     *
     * @param reply 状态栏请求的完整回复文本。
     * @return 规范化后的状态栏标记文本；无效时返回 null。
     */
    fun extractStatusBlock(reply: String): String? {
        val cleaned = fenceLine.replace(reply, "").trim()
        val open = statusBlockOpen.find(cleaned) ?: return null
        val close = statusBlockClose.findAll(cleaned)
            .lastOrNull { match -> match.range.first > open.range.last }
            ?: return null
        val inner = cleaned.substring(open.range.last + 1, close.range.first)
        if (parseSections(inner).isEmpty()) return null
        return "<StatusBlock>" + inner + "</StatusBlock>"
    }

    /**
     * 把「流式生成中被取消」的部分状态栏内容补齐为可落库的完整标记。
     *
     * 未完成时保留已接收的分区内容，由调用方与正文合并保存；已接收内容里没有任何
     * 可识别分区时返回 null（此时只保留正文）。
     *
     * @param raw 状态栏请求已接收的全部原文。
     * @return 补齐后的 `<StatusBlock>…</StatusBlock>` 文本；无有效内容时返回 null。
     */
    fun buildPartialStatusBlock(raw: String): String? {
        val open = statusBlockOpen.find(raw) ?: return null
        val innerStart = open.range.last + 1
        val lastClose = statusBlockClose.findAll(raw).lastOrNull { match -> match.range.first >= innerStart }
        val inner = if (lastClose == null) {
            raw.substring(innerStart)
        } else {
            raw.substring(innerStart, lastClose.range.first)
        }
        if (parseSections(inner).isEmpty()) return null
        return "<StatusBlock>" + inner + "</StatusBlock>"
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
                // `<details>` 未闭合时把其后的内容当作一个分区（流式/被截断场景）。
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
            // `<summary>` 未闭合：去掉起始标签，其余内容按正文处理（流式场景）。
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
            // 围栏未闭合时，其后内容全部按代码处理。
            flushCode()
        } else {
            flushPlain()
        }
        return parts
    }
}
