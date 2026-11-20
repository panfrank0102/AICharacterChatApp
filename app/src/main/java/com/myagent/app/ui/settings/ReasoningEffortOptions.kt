package com.myagent.app.ui.settings

/**
 * 思考强度选项：第一项为 null（不设置，请求中不携带 `reasoning_effort`），
 * 其余为 DeepSeek 官方 `reasoning_effort` 取值与中文标签。
 *
 * 官方文档：`none` 关闭思考模式；`low`/`high` 开启思考模式；
 * 出于兼容 `medium` 映射为 `high`（见 DeepSeek API 文档 thinking mode）。
 */
internal val REASONING_EFFORT_OPTIONS: List<Pair<String?, String>> = listOf(
    null to "不设置（不传参数，兼容其他模型）",
    "none" to "不思考",
    "low" to "低",
    "medium" to "中",
    "high" to "高",
)

/**
 * 把思考强度取值转换为界面展示文案；未知取值按"不设置"展示。
 */
internal fun reasoningEffortLabel(value: String?): String =
    REASONING_EFFORT_OPTIONS.firstOrNull { it.first == value }?.second ?: "不设置"
