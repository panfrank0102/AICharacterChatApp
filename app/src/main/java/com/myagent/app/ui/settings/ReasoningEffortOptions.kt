package com.myagent.app.ui.settings

/**
 * 思考强度选项：第一项为 null（不设置，请求中不携带 `reasoning_effort`），
 * 其余为 DeepSeek 官方允许的 `reasoning_effort` 请求取值与标签。
 *
 * 官方文档给出的「请求传入 effort → 实际映射 effort」对照：
 * `minimal`→low、`low`→low、`medium`→high、`high`→high、
 * `xhigh`→high、`max`→max、`ultra`→max；
 * 另 `none` 表示关闭思考模式（不思考）。
 */
internal val REASONING_EFFORT_OPTIONS: List<Pair<String?, String>> = listOf(
    null to "不设置（不传参数，兼容其他模型）",
    "none" to "不思考",
    "minimal" to "minimal（实际 low）",
    "low" to "low（低）",
    "medium" to "medium（实际 high）",
    "high" to "high（高）",
    "xhigh" to "xhigh（实际 high）",
    "max" to "max（最高）",
    "ultra" to "ultra（实际 max）",
)

/**
 * 把思考强度取值转换为界面展示文案；未知取值按"不设置"展示。
 */
internal fun reasoningEffortLabel(value: String?): String =
    REASONING_EFFORT_OPTIONS.firstOrNull { it.first == value }?.second ?: "不设置"
