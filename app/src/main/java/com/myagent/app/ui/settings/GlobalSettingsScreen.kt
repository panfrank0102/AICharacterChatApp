package com.myagent.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

/**
 * 全局设置页：集中维护全局提示词、默认预设、上下文长度上限与 temperature。
 *
 * @param onBack 返回上一页。
 * @param onOpenPresetList 打开模型预设列表页。
 * @param onOpenPromptEdit 打开提示词编辑页；参数为 null 表示编辑全局提示词。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSettingsScreen(
    onBack: () -> Unit,
    onOpenPresetList: () -> Unit,
    onOpenPromptEdit: (Long?) -> Unit,
) {
    val vm: GlobalSettingsViewModel = viewModel()
    val settings by vm.settings.collectAsState()
    val presets by vm.presets.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 上下文长度使用本地输入框，设置值变化（首次加载/保存成功）时重新同步。
    var contextLimitText by remember(settings.contextLimitChars) {
        mutableStateOf(settings.contextLimitChars.toString())
    }
    var contextLimitHasFocus by remember { mutableStateOf(false) }
    // temperature 拖动过程中只更新本地值，松手后才写回 DataStore。
    var temperature by remember(settings.temperature) { mutableFloatStateOf(settings.temperature) }
    var presetMenuExpanded by remember { mutableStateOf(false) }

    val selectedPresetName = presets.firstOrNull { it.id == settings.defaultPresetId }?.name ?: "无"

    /** 校验并提交上下文长度：非法时只提示，不写入。 */
    fun commitContextLimit() {
        val value = contextLimitText.trim().toIntOrNull()
        if (value == null || value !in MIN_CONTEXT_LIMIT..MAX_CONTEXT_LIMIT) {
            scope.launch { snackbarHostState.showSnackbar("请输入 1000~1000000 之间的整数") }
        } else {
            vm.setContextLimitChars(value)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("全局设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingEntryCard(
                title = "全局提示词",
                subtitle = globalPromptPreview(settings.globalPrompt),
                onClick = { onOpenPromptEdit(null) },
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { presetMenuExpanded = true },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(text = "默认预设", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = selectedPresetName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                DropdownMenu(
                    expanded = presetMenuExpanded,
                    onDismissRequest = { presetMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("无") },
                        onClick = {
                            presetMenuExpanded = false
                            vm.setDefaultPresetId(null)
                        },
                    )
                    presets.forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(preset.name) },
                            onClick = {
                                presetMenuExpanded = false
                                vm.setDefaultPresetId(preset.id)
                            },
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "上下文长度上限（字符）",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = contextLimitText,
                            onValueChange = { contextLimitText = it },
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        contextLimitHasFocus = true
                                    } else if (contextLimitHasFocus) {
                                        contextLimitHasFocus = false
                                        commitContextLimit()
                                    }
                                },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        TextButton(onClick = { commitContextLimit() }) {
                            Text("保存")
                        }
                    }
                    Text(
                        text = "允许范围 1000 ~ 1000000 个字符",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = "temperature", style = MaterialTheme.typography.titleMedium)
                        Text(text = "%.2f".format(temperature))
                    }
                    Slider(
                        value = temperature,
                        onValueChange = { temperature = it },
                        valueRange = 0f..2f,
                        onValueChangeFinished = { vm.setTemperature(temperature) },
                    )
                    Text(
                        text = "允许范围 0.00 ~ 2.00",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SettingEntryCard(
                title = "模型预设管理",
                subtitle = "新建、编辑或删除模型预设",
                onClick = onOpenPresetList,
            )
        }
    }
}

/** 可点击的设置项卡片：上方标题、下方副标题。 */
@Composable
private fun SettingEntryCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 生成全局提示词预览：未设置时显示"未设置"，过长时截断前 30 字。 */
private fun globalPromptPreview(prompt: String): String {
    val singleLine = prompt.replace('\n', ' ').trim()
    return when {
        singleLine.isEmpty() -> "未设置"
        singleLine.length > 30 -> singleLine.take(30) + "…"
        else -> singleLine
    }
}

/** 上下文长度上限的最小值。 */
private const val MIN_CONTEXT_LIMIT = 1000

/** 上下文长度上限的最大值。 */
private const val MAX_CONTEXT_LIMIT = 1_000_000
