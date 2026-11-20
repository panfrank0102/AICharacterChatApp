package com.myagent.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch

/**
 * 模型预设编辑页：编辑名称、服务地址、API Key 与模型名，保存后返回上一页。
 *
 * API Key 输入框默认掩码显示，可切换明文；编辑已有预设时留空表示保持原值。
 *
 * @param onBack 返回上一页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetEditScreen(onBack: () -> Unit) {
    val vm: PresetEditViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PresetEditViewModel(createSavedStateHandle()) }
        },
    )
    val preset by vm.preset.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var name by rememberSaveable { mutableStateOf("") }
    var baseUrl by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var apiKeyVisible by rememberSaveable { mutableStateOf(false) }
    // 思考强度：空串表示"不设置"（不发送 reasoning_effort 参数）。
    var reasoningEffort by rememberSaveable { mutableStateOf("") }
    var effortMenuExpanded by remember { mutableStateOf(false) }
    var initialized by rememberSaveable { mutableStateOf(false) }

    // 已有预设加载完成后只回填一次，避免覆盖用户正在编辑的内容。
    LaunchedEffect(preset) {
        val loaded = preset
        if (loaded != null && !initialized) {
            name = loaded.name
            baseUrl = loaded.baseUrl
            model = loaded.model
            reasoningEffort = loaded.reasoningEffort.orEmpty()
            initialized = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.presetId > 0) "编辑预设" else "新建预设") },
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("名称") },
                singleLine = true,
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("服务地址（BaseURL）") },
                placeholder = { Text("https://api.example.com/v1") },
                singleLine = true,
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = if (apiKeyVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    TextButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                        Text(if (apiKeyVisible) "隐藏" else "显示")
                    }
                },
            )
            if (vm.presetId > 0) {
                Text(
                    text = "API Key 留空表示保持原值",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("模型名") },
                placeholder = { Text("gpt-4o-mini") },
                singleLine = true,
            )
            // 思考强度（DeepSeek reasoning_effort）：不设置 / 不思考 / 低 / 中 / 高。
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = reasoningEffortLabel(reasoningEffort.ifBlank { null }),
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("思考强度") },
                    readOnly = true,
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = { effortMenuExpanded = true }) {
                            Text("选择")
                        }
                    },
                )
                DropdownMenu(
                    expanded = effortMenuExpanded,
                    onDismissRequest = { effortMenuExpanded = false },
                ) {
                    REASONING_EFFORT_OPTIONS.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                reasoningEffort = value.orEmpty()
                                effortMenuExpanded = false
                            },
                        )
                    }
                }
            }
            Text(
                text = "思考强度仅对支持 reasoning_effort 的模型生效；选择\"不设置\"则请求中不携带该参数。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    if (name.isBlank() || baseUrl.isBlank() || model.isBlank()) {
                        scope.launch {
                            snackbarHostState.showSnackbar("请填写名称、服务地址和模型名")
                        }
                    } else {
                        vm.save(
                            name = name,
                            baseUrl = baseUrl,
                            apiKeyPlain = apiKey,
                            model = model,
                            reasoningEffort = reasoningEffort.ifBlank { null },
                            onSaved = onBack,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存")
            }
        }
    }
}
