package com.myagent.app.network

/**
 * Server-Sent Events（SSE）逐行解析工具，保持纯函数、无外部依赖。
 */
object SseParser {

    /**
     * 从一行 SSE 文本中提取 data 字段内容。
     *
     * 仅当行以 "data:" 开头时返回其后内容的 trim 结果；内容为空白或不是 data 行时返回 null。
     */
    fun extractData(line: String): String? {
        if (!line.startsWith("data:")) {
            return null
        }
        return line.removePrefix("data:").trim().ifEmpty { null }
    }

    /**
     * 判断一行是否为流结束标记 "data: [DONE]"。
     */
    fun isDone(line: String): Boolean = line.trim() == "data: [DONE]"
}
