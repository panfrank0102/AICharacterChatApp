package com.myagent.app.ui.sessionsettings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlin.math.roundToInt

/** 扫描条数未加载时的兜底值，与需求文档的默认值一致。 */
private const val DEFAULT_SCAN_COUNT = 20

/** 递归深度未加载时的兜底值，与需求文档的默认值一致。 */
private const val DEFAULT_RECURSION_DEPTH = 2

/** 扫描条数输入框最多允许输入的字符数（200 为三位数）。 */
private const val SCAN_COUNT_MAX_LENGTH = 3

/**
 * 会话设置页。
 *
 * 展示并编辑当前会话的模型预设、前置提示词入口、世界书入口、世界书扫描条数与递归深度；
 * 修改会即时写回数据库，从下一条消息起生效。
 *
 * @param onBack 返回上一页。
 * @param onOpenPromptEdit 打开提示词编辑页，参数为会话 id。
 * @param onOpenWorldbook 打开当前会话的世界书列表页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSettingsScreen(
    onBack: () -> Unit,
    onOpenPromptEdit: (Long) -> Unit,
    onOpenWorldbook: () -> Unit,
) {
    val vm: SessionSettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SessionSettingsViewModel(createSavedStateHandle()) }
        },
    )
    val conversation by vm.conversation.collectAsState()
    val presets by vm.presets.collectAsState()

    var presetMenuExpanded by remember { mutableStateOf(false) }
    val selectedPresetName = presets.firstOrNull { preset -> preset.id == conversation?.presetId }?.name
        ?: "继承默认设置"

    var scanText by rememberSaveable { mutableStateOf(DEFAULT_SCAN_COUNT.toString()) }
    var scanFieldFocused by remember { mutableStateOf(false) }
    LaunchedEffect(conversation?.scanCount) {
        conversation?.scanCount?.let { scanCount -> scanText = scanCount.toString() }
    }
    val commitScanCount: () -> Unit = {
        val parsed = scanText.trim().toIntOrNull() ?: DEFAULT_SCAN_COUNT
        val clamped = parsed.coerceIn(0, 200)
        scanText = clamped.toString()
        vm.setScanCount(clamped)
    }

    var draggingDepth by remember { mutableStateOf<Float?>(null) }
    val depthValue = draggingDepth
        ?: (conversation?.recursionDepth ?: DEFAULT_RECURSION_DEPTH).toFloat()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("会话设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("模型预设", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { presetMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = selectedPresetName,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                DropdownMenu(
                    expanded = presetMenuExpanded,
                    onDismissRequest = { presetMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("继承默认设置") },
                        onClick = {
                            presetMenuExpanded = false
                            vm.setPreset(null)
                        },
                    )
                    presets.forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(preset.name) },
                            onClick = {
                                presetMenuExpanded = false
                                vm.setPreset(preset.id)
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            SettingsEntryRow(
                title = "前置提示词",
                subtitle = truncate(
                    text = conversation?.systemPrompt.orEmpty().ifBlank { "未设置" },
                    maxLength = 30,
                ),
                onClick = { onOpenPromptEdit(vm.conversationId) },
            )
            SettingsEntryRow(
                title = "世界书",
                subtitle = "管理本会话的世界书条目",
                onClick = onOpenWorldbook,
            )
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Text("世界书扫描条数", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "参与关键词扫描的历史消息条数，0 表示关闭扫描",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = scanText,
                    onValueChange = { input ->
                        scanText = input.filter { char -> char.isDigit() }.take(SCAN_COUNT_MAX_LENGTH)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { focusState ->
                            if (scanFieldFocused && !focusState.isFocused) {
                                commitScanCount()
                            }
                            scanFieldFocused = focusState.isFocused
                        },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("0 ~ 200") },
                )
                TextButton(onClick = commitScanCount) {
                    Text("保存")
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("世界书递归深度", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "命中的条目正文继续参与匹配的层数，0 表示不递归",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "当前深度：${depthValue.roundToInt()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = depthValue,
                onValueChange = { value -> draggingDepth = value },
                valueRange = 0f..5f,
                steps = 4,
                onValueChangeFinished = {
                    val current = draggingDepth
                        ?: (conversation?.recursionDepth ?: DEFAULT_RECURSION_DEPTH).toFloat()
                    val rounded = current.roundToInt().coerceIn(0, 5)
                    draggingDepth = null
                    vm.setRecursionDepth(rounded)
                },
            )
        }
    }
}

/** 会话设置页中的可点击设置项：主标题 + 副标题。 */
@Composable
private fun SettingsEntryRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 把文本截断到 [maxLength] 个字符，超出时追加省略号。 */
private fun truncate(text: String, maxLength: Int): String =
    if (text.length <= maxLength) text else text.take(maxLength) + "…"
