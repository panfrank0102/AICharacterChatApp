package com.myagent.app.ui.sessionsettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch

/** 表单校验失败时的提示文案。 */
private const val EMPTY_FORM_MESSAGE = "请填写标题、关键词和正文"

/**
 * 世界书条目编辑页。
 *
 * 新建条目时表单为空，编辑已有条目时在条目加载完成后回填一次；
 * 标题、关键词、正文均为空（或关键词拆分后为空）时不允许保存并给出提示。
 *
 * @param onBack 返回上一页；保存成功后也通过它返回。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldbookEntryEditScreen(onBack: () -> Unit) {
    val vm: WorldbookEntryEditViewModel = viewModel(
        factory = viewModelFactory {
            initializer { WorldbookEntryEditViewModel(createSavedStateHandle()) }
        },
    )
    val entry by vm.entry.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var title by rememberSaveable { mutableStateOf("") }
    var keywordsText by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var priorityText by rememberSaveable { mutableStateOf("0") }
    var enabled by rememberSaveable { mutableStateOf(true) }
    var formFilled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(entry) {
        val loaded = entry
        if (loaded != null && !formFilled) {
            title = loaded.title
            keywordsText = loaded.keywords
            content = loaded.content
            priorityText = loaded.priority.toString()
            enabled = loaded.enabled
            formFilled = true
        }
    }

    val saveEntry: () -> Unit = {
        val keywords = keywordsText.split('\n', ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (title.isBlank() || keywords.isEmpty() || content.isBlank()) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(EMPTY_FORM_MESSAGE)
            }
        } else {
            vm.save(
                title = title,
                keywordsText = keywordsText,
                content = content,
                priorityText = priorityText,
                enabled = enabled,
                onSaved = onBack,
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.entryId > 0L) "编辑条目" else "新建条目") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = saveEntry) {
                        Text("保存")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { input -> title = input },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("标题") },
                singleLine = true,
            )
            OutlinedTextField(
                value = keywordsText,
                onValueChange = { input -> keywordsText = input },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("关键词（每行一个，可用逗号分隔）") },
                minLines = 2,
                maxLines = 4,
            )
            OutlinedTextField(
                value = content,
                onValueChange = { input -> content = input },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = { Text("正文") },
            )
            OutlinedTextField(
                value = priorityText,
                onValueChange = { input -> priorityText = input },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("优先级（数字越大越靠前）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("启用", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = enabled,
                    onCheckedChange = { checked -> enabled = checked },
                )
            }
        }
    }
}
