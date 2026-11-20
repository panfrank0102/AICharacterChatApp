package com.myagent.app.domain

/**
 * 状态栏优化功能的内置指令文本与解析工具。
 *
 * 会话里可以自定义这两条临时指令；自定义内容为空白时使用这里的默认文本。
 * 这两条指令只作为对应请求的临时 system 消息追加，不写入数据库、不进入历史消息。
 */
object StatusBarInstructions {

    /** 正文请求的默认附加指令：要求模型只输出正文。 */
    const val DEFAULT_BODY_INSTRUCTION =
        "【本次回复要求】只输出正文剧情，不要输出 <StatusBlock> 状态栏或任何状态栏标记；" +
            "状态栏会由另一次请求单独生成。"

    /** 状态栏请求的默认附加指令：要求模型只输出更新后的状态栏。 */
    const val DEFAULT_STATUS_INSTRUCTION =
        "根据以上对话，输出本回合结束后的最新 <StatusBlock> 状态栏。" +
            "只输出 <StatusBlock>…</StatusBlock> 标记，不要输出任何其他内容；" +
            "保留原有全部分区（如世界信息、各角色状态等）并按本回合发生的变化更新。"

    /** 解析正文请求实际使用的指令：自定义内容空白时回落到默认文本。 */
    fun resolveBodyInstruction(custom: String): String =
        custom.ifBlank { DEFAULT_BODY_INSTRUCTION }

    /** 解析状态栏请求实际使用的指令：自定义内容空白时回落到默认文本。 */
    fun resolveStatusInstruction(custom: String): String =
        custom.ifBlank { DEFAULT_STATUS_INSTRUCTION }

    /**
     * 计算状态栏请求使用的思考强度。
     *
     * - 会话未开启「强制不思考」：完全沿用预设的思考强度（含 null = 不设置）；
     * - 开启「强制不思考」：仅当预设本身已设置思考强度时才使用 "none"；
     *   若预设为「不设置」（null），保持不发送该参数，以兼容不支持思考强度的模型。
     */
    fun resolveStatusReasoningEffort(presetEffort: String?, forceNoThinking: Boolean): String? {
        if (!forceNoThinking) return presetEffort
        return if (presetEffort == null) null else REASONING_EFFORT_NONE
    }

    /** 关闭思考模式的 reasoning_effort 取值。 */
    const val REASONING_EFFORT_NONE = "none"
}
