package com.myagent.app.ui.cardimport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myagent.app.domain.import.CharacterCard

/** 描述摘要的最大展示字符数。 */
private const val DESCRIPTION_PREVIEW_LENGTH = 200

/** 提示词预览的最大展示字符数。 */
private const val PROMPT_PREVIEW_LENGTH = 300

/** 开场白预览的最大展示字符数。 */
private const val GREETING_PREVIEW_LENGTH = 80

/**
 * 角色卡导入预览页。
 *
 * 展示待导入卡片的名称、作者/版本、描述摘要、世界书统计、导入后的会话提示词，
 * 并提供备用开场白多选、自定义开场输入，确认后创建会话并跳转到新会话的聊天页。
 *
 * @param onBack 返回上一页。
 * @param onImported 导入成功后的回调，参数为新建会话 id。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardImportScreen(onBack: () -> Unit, onImported: (Long) -> Unit) {
    val vm: CardImportViewModel = viewModel()
    val card by vm.card.collectAsState()
    val importing by vm.importing.collectAsState()
    val error by vm.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        val message = error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        vm.clearError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导入角色卡") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val currentCard = card
        if (currentCard == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("未选择角色卡，请返回会话列表重新选择")
            }
        } else {
            CardImportContent(
                card = currentCard,
                promptPreview = vm.promptPreview,
                totalWorldbookCount = vm.totalWorldbookCount,
                keywordlessWorldbookCount = vm.keywordlessWorldbookCount,
                importing = importing,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onImport = { selectedIndexes, customOpening ->
                    vm.import(
                        selectedGreetingIndexes = selectedIndexes,
                        customOpening = customOpening,
                        onDone = onImported,
                    )
                },
            )
        }
    }
}

/**
 * 导入预览的滚动内容区。
 *
 * @param card 待导入的角色卡
 * @param promptPreview 导入后将写入会话的系统提示词
 * @param totalWorldbookCount 世界书条目总数
 * @param keywordlessWorldbookCount 没有关键词的条目数（会导入但需补充关键词才会触发）
 * @param importing 是否正在导入
 * @param modifier 外部修饰符
 * @param onImport 点击"导入"按钮的回调，参数为选中的开场白下标与自定义开场内容
 */
@Composable
private fun CardImportContent(
    card: CharacterCard,
    promptPreview: String,
    totalWorldbookCount: Int,
    keywordlessWorldbookCount: Int,
    importing: Boolean,
    modifier: Modifier = Modifier,
    onImport: (Set<Int>, String) -> Unit,
) {
    var selectedGreetings by remember { mutableStateOf(emptySet<Int>()) }
    var customOpeningChecked by remember { mutableStateOf(false) }
    var customOpeningText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1) 卡片基本信息：名称、作者/版本、描述摘要。
        Text(text = card.name, style = MaterialTheme.typography.titleLarge)
        val creatorAndVersion = buildList {
            if (card.creator.isNotBlank()) add("作者：${card.creator}")
            if (card.characterVersion.isNotBlank()) add("版本：${card.characterVersion}")
        }.joinToString("　")
        if (creatorAndVersion.isNotEmpty()) {
            Text(
                text = creatorAndVersion,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = previewText(card.description, DESCRIPTION_PREVIEW_LENGTH),
            style = MaterialTheme.typography.bodyMedium,
        )

        // 2) 世界书统计与无关键词提示。
        Text(text = "世界书：共 $totalWorldbookCount 条", style = MaterialTheme.typography.bodyMedium)
        if (keywordlessWorldbookCount > 0) {
            Text(
                text = "注意：有 $keywordlessWorldbookCount 条没有关键词，导入后将暂时不会触发，" +
                    "可在导入后的世界书中补充关键词",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // 3) 导入后的会话提示词预览。
        Text(text = "导入后的会话提示词", style = MaterialTheme.typography.titleSmall)
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (promptPreview.isEmpty()) {
                    "（无）"
                } else {
                    previewText(promptPreview, PROMPT_PREVIEW_LENGTH)
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        }

        // 4) 备用开场白多选（仅卡片提供备用开场白时显示）。
        if (card.alternateGreetings.isNotEmpty()) {
            Text(
                text = "备用开场白（可多选，将作为 AI 首条消息插入）",
                style = MaterialTheme.typography.titleSmall,
            )
            card.alternateGreetings.forEachIndexed { index, greeting ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Checkbox(
                        checked = index in selectedGreetings,
                        onCheckedChange = { checked ->
                            selectedGreetings = if (checked) {
                                (selectedGreetings + index).toSet()
                            } else {
                                (selectedGreetings - index).toSet()
                            }
                        },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "开场白 ${index + 1}（${greeting.length} 字）",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = previewText(greeting, GREETING_PREVIEW_LENGTH),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // 5) 自定义开场白。
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = customOpeningChecked,
                onCheckedChange = { checked -> customOpeningChecked = checked },
            )
            Text(
                text = "自定义开场（将作为第一条用户消息插入）",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (customOpeningChecked) {
            OutlinedTextField(
                value = customOpeningText,
                onValueChange = { text -> customOpeningText = text },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("输入开场内容…") },
                minLines = 3,
                maxLines = 4,
            )
        }

        // 6) 宏替换说明。
        Text(
            text = "{{user}} 会在发送时替换为会话设置中的用户称呼（默认\"用户\"）；{{char}} 已替换为卡名。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 7) 确认导入。
        Button(
            onClick = {
                onImport(
                    selectedGreetings,
                    if (customOpeningChecked) customOpeningText else "",
                )
            },
            enabled = !importing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = if (importing) "导入中…" else "导入")
        }
    }
}

/** 截断过长文本，超出长度时在末尾追加省略号。 */
private fun previewText(text: String, maxLength: Int): String =
    if (text.length <= maxLength) text else text.take(maxLength) + "…"
