package com.myagent.app.domain

/**
 * token 数量粗估器。
 *
 * 按需求文档 FR-6 的口径估算：CJK 类字符（中文、日文假名、谚文、CJK 标点、全角字符等）
 * 每个字符按 1 token 计；其余字符按每 4 个字符 1 token 计，不足 4 个也向上取整为 1 个 token。
 *
 * 注意：这是用于上下文长度预算控制的**粗估**，并非真实 tokenizer 的结果，不要求精确；
 * 遍历以 UTF-16 码元（[Char]）为单位，代理对（如 emoji）会被拆成两个码元分别计数。
 */
object TokenEstimator {

    /** 非 CJK 字符每多少个字符折算 1 个 token。 */
    private const val CHARS_PER_TOKEN = 4

    /**
     * 估算 [text] 的 token 数量；空字符串返回 0。
     */
    fun estimate(text: String): Int {
        if (text.isEmpty()) return 0
        var cjkCount = 0
        var otherCount = 0
        for (ch in text) {
            if (isCjk(ch)) {
                cjkCount++
            } else {
                otherCount++
            }
        }
        return cjkCount + (otherCount + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN
    }

    /**
     * 判断字符是否属于 CJK 类字符。
     *
     * 采用 Unicode 区块判断，范围说明：
     * - 0x2E80..0x2EFF：CJK 部首补充
     * - 0x3000..0x303F：CJK 符号与标点（含中文顿号、句号等）
     * - 0x3040..0x30FF：日文平假名与片假名
     * - 0x3400..0x4DBF：CJK 扩展 A
     * - 0x4E00..0x9FFF：CJK 统一表意文字
     * - 0xAC00..0xD7AF：谚文音节
     * - 0xF900..0xFAFF：CJK 兼容表意文字
     * - 0xFF00..0xFFEF：半角及全角形式（含全角字母、数字、标点、片假名）
     */
    private fun isCjk(ch: Char): Boolean = when (ch.code) {
        in 0x2E80..0x2EFF,
        in 0x3000..0x303F,
        in 0x3040..0x30FF,
        in 0x3400..0x4DBF,
        in 0x4E00..0x9FFF,
        in 0xAC00..0xD7AF,
        in 0xF900..0xFAFF,
        in 0xFF00..0xFFEF -> true

        else -> false
    }
}
