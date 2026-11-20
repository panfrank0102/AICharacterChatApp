package com.myagent.app.domain.import

/**
 * 酒馆（SillyTavern）角色卡的领域模型。
 *
 * 该模型与具体卡片文件格式（V1 裸结构、V2/V3 的 spec + data 包装）解耦，
 * 由 [CharacterCardParser] 负责把不同来源的 JSON 统一映射到这里。
 *
 * @property name 角色名称
 * @property description 角色描述
 * @property personality 性格设定
 * @property scenario 场景设定
 * @property firstMes 第一条问候语
 * @property mesExample 对话示例
 * @property systemPrompt 系统提示词
 * @property postHistoryInstructions 历史消息之后的补充指令
 * @property creator 角色作者
 * @property characterVersion 角色版本
 * @property alternateGreetings 备选问候语列表
 * @property worldBook 角色附带的世界书，可能为空
 */
data class CharacterCard(
    val name: String,
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMes: String = "",
    val mesExample: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val creator: String = "",
    val characterVersion: String = "",
    val alternateGreetings: List<String> = emptyList(),
    val worldBook: WorldBook? = null,
)

/**
 * 角色卡附带的世界书。
 *
 * @property name 世界书名称
 * @property scanDepth 扫描历史消息的深度
 * @property tokenBudget 世界书注入的 token 预算
 * @property recursiveScanning 是否启用递归扫描
 * @property entries 世界书条目列表
 */
data class WorldBook(
    val name: String = "",
    val scanDepth: Int? = null,
    val tokenBudget: Int? = null,
    val recursiveScanning: Boolean? = null,
    val entries: List<WorldBookEntry> = emptyList(),
)

/**
 * 世界书中的单条条目。
 *
 * @property keys 主关键词列表
 * @property secondaryKeys 次关键词列表
 * @property content 条目正文
 * @property priority 优先级
 * @property enabled 是否启用
 * @property constant 是否常驻注入
 * @property selective 是否启用选择性匹配
 * @property caseSensitive 是否区分大小写
 * @property comment 条目备注
 */
data class WorldBookEntry(
    val keys: List<String> = emptyList(),
    val secondaryKeys: List<String> = emptyList(),
    val content: String = "",
    val priority: Int? = null,
    val enabled: Boolean = true,
    val constant: Boolean = false,
    val selective: Boolean = false,
    val caseSensitive: Boolean = false,
    val comment: String = "",
)

/**
 * 角色卡数据无效（缺少卡片数据、base64 解码失败、JSON 结构不合法等）时抛出的异常。
 *
 * @param message 面向调用方的中文错误说明
 */
class CharacterCardParseException(message: String) : Exception(message)
