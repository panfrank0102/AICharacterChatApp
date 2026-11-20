package com.myagent.app.ui.navigation

/**
 * 应用路由表。所有页面跳转统一使用 [Routes] 提供的常量和构造方法。
 */
object Routes {

    const val ARG_CONVERSATION_ID = "conversationId"
    const val ARG_PRESET_ID = "presetId"
    const val ARG_ENTRY_ID = "entryId"
    const val ARG_PROMPT_KIND = "promptKind"

    /** 提示词编辑类型：会话前置提示词。 */
    const val PROMPT_KIND_SESSION = "session"

    /** 提示词编辑类型：会话后置提示词。 */
    const val PROMPT_KIND_POST_HISTORY = "postHistory"

    /** 状态栏优化·正文请求附加指令。 */
    const val PROMPT_KIND_STATUS_BODY = "statusBody"

    /** 状态栏优化·状态栏请求附加指令。 */
    const val PROMPT_KIND_STATUS_BAR = "statusBar"

    /** 会话列表页。 */
    const val CONVERSATION_LIST = "conversationList"

    /** 聊天页。 */
    const val CHAT = "chat/{$ARG_CONVERSATION_ID}"

    /** 全局设置页。 */
    const val GLOBAL_SETTINGS = "globalSettings"

    /** 模型预设列表页。 */
    const val PRESET_LIST = "presetList"

    /** 模型预设编辑页（presetId = -1 表示新建）。 */
    const val PRESET_EDIT = "presetEdit?$ARG_PRESET_ID={$ARG_PRESET_ID}"

    /** 会话设置页。 */
    const val SESSION_SETTINGS = "sessionSettings/{$ARG_CONVERSATION_ID}"

    /** 提示词编辑页（conversationId = -1 表示编辑全局提示词；promptKind 区分前置/后置）。 */
    const val PROMPT_EDIT = "promptEdit?$ARG_CONVERSATION_ID={$ARG_CONVERSATION_ID}&$ARG_PROMPT_KIND={$ARG_PROMPT_KIND}"

    /** 世界书列表页。 */
    const val WORLDBOOK = "worldbook/{$ARG_CONVERSATION_ID}"

    /** 世界书条目编辑页（entryId = -1 表示新建）。 */
    const val WORLDBOOK_ENTRY_EDIT = "worldbookEntryEdit/{$ARG_CONVERSATION_ID}?$ARG_ENTRY_ID={$ARG_ENTRY_ID}"

    /** 角色卡导入预览页。 */
    const val CARD_IMPORT = "cardImport"

    fun chat(id: Long): String = "chat/$id"

    fun sessionSettings(id: Long): String = "sessionSettings/$id"

    fun promptEdit(
        conversationId: Long?,
        kind: String = PROMPT_KIND_SESSION,
    ): String = "promptEdit?conversationId=${conversationId ?: -1L}&promptKind=$kind"

    fun worldbook(id: Long): String = "worldbook/$id"

    fun worldbookEntryEdit(conversationId: Long, entryId: Long?): String =
        "worldbookEntryEdit/$conversationId?entryId=${entryId ?: -1L}"

    fun presetEdit(id: Long?): String = "presetEdit?presetId=${id ?: -1L}"
}
