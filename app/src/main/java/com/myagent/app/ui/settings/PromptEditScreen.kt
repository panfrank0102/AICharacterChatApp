package com.myagent.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/**
 * 提示词编辑页：编辑全局提示词、会话前置提示词或会话后置提示词，右上角"保存"写回并返回。
 *
 * @param onBack 返回上一页（保存成功后也会调用）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptEditScreen(onBack: () -> Unit) {
    val vm: PromptEditViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PromptEditViewModel(createSavedStateHandle()) }
        },
    )
    val text by vm.text.collectAsState()

    val title = when {
        vm.conversationId == null -> "全局提示词"
        vm.isPostHistory -> "后置提示词"
        vm.isStatusBody -> "状态栏正文指令"
        vm.isStatusBar -> "状态栏指令"
        else -> "会话提示词"
    }
    val placeholder = when {
        vm.isPostHistory ->
            "输入后置提示词（每轮插在对话历史之后的 system 指令，如输出格式/状态栏要求）…"
        vm.isStatusBody ->
            "输入正文请求的附加指令（要求模型只输出正文、不要输出状态栏；留空恢复默认）…"
        vm.isStatusBar ->
            "输入状态栏请求的附加指令（要求模型只输出更新后的 <StatusBlock>；留空恢复默认）…"
        else -> "输入人设/扮演规则等系统提示词…"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { vm.save(onBack) }) {
                        Text("保存")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { vm.text.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                placeholder = { Text(placeholder) },
            )
        }
    }
}
