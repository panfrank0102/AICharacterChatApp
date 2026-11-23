package com.myagent.app.domain.story

/**
 * 剧情增强功能的总结指令与解析工具（纯函数，可单元测试）。
 *
 * 剧情总结请求的结构固定为两条消息：
 * [system] 指令 + [user] 需要总结的 AI 回复正文（已剔除状态栏标记）。
 */
object StorySummaryInstructions {

    /** 无分支模式：只要求输出 100 字以内剧情总结。 */
    const val SUMMARY =
        "请将以下 AI 回复总结为 100 字以内的剧情总结。只输出总结内容本身，不要任何前缀或解释。"

    /** 分支模式：要求输出总结与四个备选剧情方向，格式固定便于解析。 */
    const val SUMMARY_WITH_BRANCHES =
        "请将以下 AI 回复总结为 100 字以内的剧情总结，并给出四个可能的下一步剧情备选选项。" +
            "严格按以下格式输出（不要输出任何其他内容）：\n" +
            "总结：<100字以内的总结>\n" +
            "选项1：<内容>\n" +
            "选项2：<内容>\n" +
            "选项3：<内容>\n" +
            "选项4：<内容>"

    /** 按是否开启分支选择返回对应指令。 */
    fun summaryInstruction(withBranches: Boolean): String =
        if (withBranches) SUMMARY_WITH_BRANCHES else SUMMARY
}

/**
 * 剧情总结请求回复的解析器。
 */
object StorySummaryParser {

    /** 解析结果：总结文本 + 分支选项（可能不足四个）。 */
    data class Result(val summary: String, val branches: List<String>)

    private val summaryLine = Regex("^\\s*总结\\s*[：:]\\s*(.*)$")
    private val optionLine = Regex("^\\s*选项\\s*([1-4])\\s*[：:]\\s*(.*)$")
    private val fenceLine = Regex("(?m)^\\s*```[^\\n]*$")

    /**
     * 解析一次剧情总结请求的回复。
     *
     * 容忍回复被 ``` 代码围栏包裹；分支模式下若模型没有按格式输出任何选项，
     * 则把整段文本当作总结（退化为无分支）；完全解析不出有效内容时返回 null（视为失败）。
     *
     * @param reply 总结请求的完整回复。
     * @param expectBranches 该会话是否开启分支选择。
     * @return 解析结果；无效时返回 null。
     */
    fun parse(reply: String, expectBranches: Boolean): Result? {
        val cleaned = fenceLine.replace(reply, "").trim()
        if (cleaned.isBlank()) return null

        if (!expectBranches) {
            val summary = cleaned
                .removePrefix("总结：")
                .removePrefix("总结:")
                .trim()
            return if (summary.isBlank()) null else Result(summary, emptyList())
        }

        val options = arrayOfNulls<String>(4)
        var summary: String? = null
        for (line in cleaned.lineSequence()) {
            val summaryMatch = summaryLine.matchEntire(line)
            if (summaryMatch != null) {
                summary = summaryMatch.groupValues[1].trim()
            } else {
                val optionMatch = optionLine.matchEntire(line)
                if (optionMatch != null) {
                    val index = optionMatch.groupValues[1].toInt() - 1
                    options[index] = optionMatch.groupValues[2].trim()
                }
            }
        }

        val resolvedSummary = summary?.takeIf { it.isNotBlank() }
            ?: if (options.all { it == null }) cleaned else null
        if (resolvedSummary.isNullOrBlank()) return null
        val branches = options.mapNotNull { it?.takeIf { text -> text.isNotBlank() } }
        return Result(resolvedSummary, branches)
    }
}
