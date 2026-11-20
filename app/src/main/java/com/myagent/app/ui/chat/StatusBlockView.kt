package com.myagent.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myagent.app.domain.statusbar.ChatContentBlock
import com.myagent.app.domain.statusbar.StatusBlockParser
import com.myagent.app.domain.statusbar.StatusPart
import com.myagent.app.domain.statusbar.StatusSection

/** 状态栏正文使用的行高，比默认 bodySmall 稍大，便于阅读多行文本。 */
private val PanelBodyLineHeight = 18.sp

/**
 * 渲染一条聊天消息内容：普通文本 + 状态栏面板。
 *
 * 消息中的 `<StatusBlock>` 标记会被解析为卡片式状态栏，其余内容照常显示；
 * 未闭合或无法识别的标记会退化为普通文本，保证不丢内容。
 *
 * @param content 消息原文。
 * @param streamingStatusText 状态栏优化中尚未完成的流式状态栏原文；
 * 非空时在正文后渐进渲染状态栏面板（收到开标签即建面板、内容随流填充、结束标签不显示）。
 * @param modifier 作用于整个内容列的修饰符（例如气泡内边距）。
 */
@Composable
internal fun ChatMessageContent(
    content: String,
    streamingStatusText: String = "",
    modifier: Modifier = Modifier,
) {
    val blocks = remember(content) { StatusBlockParser.parse(content) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is ChatContentBlock.Text -> Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodyMedium,
                )

                is ChatContentBlock.StatusPanel -> StatusPanelCard(panel = block)
            }
        }
        if (streamingStatusText.isNotEmpty()) {
            StreamingStatusPanel(statusText = streamingStatusText)
        }
    }
}

/**
 * 状态栏优化·流式阶段的状态栏面板：
 * 一旦收到 `<StatusBlock>` 开标签即渲染面板（空面板也显示"状态栏（更新中…）"标题），
 * 分区与内容随增量持续填充；`</StatusBlock>` 不显示。
 */
@Composable
private fun StreamingStatusPanel(statusText: String) {
    val sections = remember(statusText) { StatusBlockParser.parseStreamingStatus(statusText) }
    if (!StatusBlockParser.isStatusBlockStarted(statusText)) return
    StatusPanelCard(
        panel = ChatContentBlock.StatusPanel(sections),
        streaming = true,
    )
}

/**
 * 状态栏卡片：标题行点击可折叠整块，默认展开。
 *
 * @param panel 已解析出的状态栏面板数据。
 * @param streaming 是否处于流式填充中（标题附带"更新中"提示；空面板也渲染）。
 */
@Composable
private fun StatusPanelCard(
    panel: ChatContentBlock.StatusPanel,
    streaming: Boolean = false,
) {
    // 防御：静态渲染时没有分区不渲染；流式中空面板也要先显示框架。
    if (panel.sections.isEmpty() && !streaming) return

    var expanded by rememberSaveable { mutableStateOf(true) }
    val shape = RoundedCornerShape(8.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (streaming) "状态栏（更新中…）" else "状态栏",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = expandIcon(expanded),
                    contentDescription = if (expanded) "折叠状态栏" else "展开状态栏",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (expanded) {
                Column(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    panel.sections.forEach { section ->
                        StatusSectionView(section = section)
                    }
                }
            }
        }
    }
}

/**
 * 状态栏中的单个分区：标题行点击可折叠，默认展开；展开时渲染普通文本与代码块。
 *
 * @param section 分区数据。
 */
@Composable
private fun StatusSectionView(section: StatusSection) {
    var expanded by rememberSaveable { mutableStateOf(true) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = expandIcon(expanded),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = section.title.ifBlank { "详情" },
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (expanded) {
            Column(
                modifier = Modifier.padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                section.parts.forEach { part ->
                    when (part) {
                        is StatusPart.Plain -> Text(
                            text = part.text,
                            style = MaterialTheme.typography.bodySmall.copy(
                                lineHeight = PanelBodyLineHeight,
                            ),
                        )

                        is StatusPart.Code -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(4.dp),
                                )
                                .padding(6.dp),
                        ) {
                            Text(
                                text = part.text,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 根据折叠状态返回标题箭头图标。 */
private fun expandIcon(expanded: Boolean): ImageVector =
    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown
