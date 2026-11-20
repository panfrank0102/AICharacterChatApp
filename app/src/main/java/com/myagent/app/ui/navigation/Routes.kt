package com.myagent.app.ui.navigation

/**
 * 应用路由表。所有页面跳转统一使用 [Routes] 提供的常量和构造方法。
 */
object Routes {

    const val ARG_CONVERSATION_ID = "conversationId"
    const val ARG_PRESET_ID = "presetId"
    const val ARG_ENTRY_ID = "entryId"

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

    /** 提示词编辑页（conversationId = -1 表示编辑全局提示词）。 */
    const val PROMPT_EDIT = "promptEdit?$ARG_CONVERSATION_ID={$ARG_CONVERSATION_ID}"

    /** 世界书列表页。 */
    const val WORLDBOOK = "worldbook/{$ARG_CONVERSATION_ID}"

    /** 世界书条目编辑页（entryId = -1 表示新建）。 */
    const val WORLDBOOK_ENTRY_EDIT = "worldbookEntryEdit/{$ARG_CONVERSATION_ID}?$ARG_ENTRY_ID={$ARG_ENTRY_ID}"

    /** 角色卡导入预览页。 */
    const val CARD_IMPORT = "cardImport"

    fun chat(id: Long): String = "chat/$id"

    fun sessionSettings(id: Long): String = "sessionSettings/$id"

    fun promptEdit(conversationId: Long?): String = "promptEdit?conversationId=${conversationId ?: -1L}"

    fun worldbook(id: Long): String = "worldbook/$id"

    fun worldbookEntryEdit(conversationId: Long, entryId: Long?): String =
        "worldbookEntryEdit/$conversationId?entryId=${entryId ?: -1L}"

    fun presetEdit(id: Long?): String = "presetEdit?presetId=${id ?: -1L}"
}
