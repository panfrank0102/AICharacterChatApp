package com.myagent.app.ui.cardimport

import com.myagent.app.domain.import.CharacterCard

/**
 * 角色卡导入的进程内临时状态。
 *
 * 会话列表页解析出角色卡后把它暂存在这里，导入预览页读取并展示；
 * 该状态不参与持久化，导入成功后由预览页清空。
 */
object ImportState {

    /** 最近一次解析成功的角色卡；未选择角色卡或已被消费时为 null。 */
    var pendingCard: CharacterCard? = null
}
