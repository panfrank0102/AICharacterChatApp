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
 * 提示词编辑页：编辑全局提示词或某个会话的提示词，右上角"保存"写回并返回。
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.conversationId == null) "全局提示词" else "会话提示词") },
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
                placeholder = { Text("输入人设/扮演规则等系统提示词…") },
            )
        }
    }
}
