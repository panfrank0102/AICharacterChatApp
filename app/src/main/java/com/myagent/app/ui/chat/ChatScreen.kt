package com.myagent.app.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 用户消息的 role 取值。 */
private const val ROLE_USER = "user"

/** AI 回复消息的 role 取值。 */
private const val ROLE_ASSISTANT = "assistant"

/** 流式气泡在 LazyColumn 中的固定 key。 */
private const val STREAMING_ITEM_KEY = "streaming"

/**
 * 聊天页：展示当前会话的消息列表，支持流式输出、停止生成、重新生成、复制与删除。
 *
 * 消息区使用 [LazyColumn] 的 reverseLayout，使最新消息始终贴靠底部；流式输出时在底部
 * 额外渲染一个带光标的 AI 气泡；任一消息气泡长按可复制，点击「删除」会二次确认后
 * 删除该消息及其之后的全部消息。
 *
 * @param onBack 返回上一页。
 * @param onOpenSessionSettings 打开当前会话的设置页。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onOpenSessionSettings: () -> Unit,
) {
    val vm: ChatViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ChatViewModel(createSavedStateHandle())
            }
        },
    )

    val messages by vm.messages.collectAsState()
    val conversation by vm.conversation.collectAsState()
    val inputText by vm.inputText.collectAsState()
    val streaming by vm.streaming.collectAsState()
    val streamingText by vm.streamingText.collectAsState()
    val error by vm.error.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(error) {
        val message = error ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = "重试",
        )
        if (result == SnackbarResult.ActionPerformed) {
            vm.retry()
        }
        vm.clearError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = conversation?.title?.ifBlank { "新会话" } ?: "聊天")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSessionSettings) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "会话设置")
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (streaming) {
                    item(key = STREAMING_ITEM_KEY) {
                        MessageBubble(
                            content = streamingText + "▌",
                            isUser = false,
                            showRegenerate = false,
                            onLongClick = {},
                            onDelete = null,
                            onRegenerate = {},
                        )
                    }
                }
                items(messages.asReversed(), key = { it.id }) { message ->
                    val isLastMessage = message.id == messages.lastOrNull()?.id
                    MessageBubble(
                        content = message.content,
                        isUser = message.role == ROLE_USER,
                        showRegenerate = !streaming &&
                            message.role == ROLE_ASSISTANT &&
                            isLastMessage,
                        onLongClick = {
                            copyToClipboard(
                                clipboardManager = clipboardManager,
                                scope = scope,
                                snackbarHostState = snackbarHostState,
                                text = message.content,
                            )
                        },
                        onDelete = { pendingDeleteId = message.id },
                        onRegenerate = { vm.regenerate() },
                    )
                }
            }

            ChatInputBar(
                inputText = inputText,
                streaming = streaming,
                onInputChange = { vm.inputText.value = it },
                onSend = { vm.send() },
                onStop = { vm.stop() },
            )
        }
    }

    val deleteTargetId = pendingDeleteId
    if (deleteTargetId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(text = "删除消息") },
            text = { Text(text = "删除该消息及之后所有消息？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteFrom(deleteTargetId)
                        pendingDeleteId = null
                    },
                ) {
                    Text(text = "删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text(text = "取消")
                }
            },
        )
    }
}

/**
 * 单条消息气泡：用户消息靠右、AI 消息靠左，长按复制，下方提供删除按钮；
 * 最后一条 AI 回复额外显示「重新生成」。
 *
 * @param content 气泡正文。
 * @param isUser 是否为用户消息（决定对齐方式与配色）。
 * @param showRegenerate 是否显示「重新生成」按钮。
 * @param onLongClick 长按气泡的回调（复制内容）。
 * @param onDelete 删除按钮回调；为 null 时不显示删除按钮（如流式气泡）。
 * @param onRegenerate 「重新生成」按钮回调。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    content: String,
    isUser: Boolean,
    showRegenerate: Boolean,
    onLongClick: () -> Unit,
    onDelete: (() -> Unit)?,
    onRegenerate: () -> Unit,
) {
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongClick,
                ),
            shape = RoundedCornerShape(12.dp),
            color = bubbleColor,
            contentColor = textColor,
        ) {
            Text(
                text = content,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            onDelete?.let { deleteAction ->
                TextButton(onClick = deleteAction) {
                    Text(text = "删除", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (showRegenerate) {
                TextButton(onClick = onRegenerate) {
                    Text(text = "重新生成", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/**
 * 底部输入区：多行输入框 + 「发送 / 停止」按钮。
 *
 * @param inputText 当前输入内容。
 * @param streaming 是否正在流式输出；为 true 时按钮切换为「停止」。
 * @param onInputChange 输入变化回调。
 * @param onSend 点击「发送」回调。
 * @param onStop 点击「停止」回调。
 */
@Composable
private fun ChatInputBar(
    inputText: String,
    streaming: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(text = "输入消息…") },
            maxLines = 4,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = {
                if (streaming) onStop() else onSend()
            },
            modifier = Modifier.padding(bottom = 4.dp),
            enabled = streaming || inputText.isNotBlank(),
        ) {
            Text(text = if (streaming) "停止" else "发送")
        }
    }
}

/**
 * 把文本写入剪贴板并弹出「已复制」提示。
 *
 * @param clipboardManager 剪贴板管理器。
 * @param scope 用于展示 snackbar 的协程作用域。
 * @param snackbarHostState snackbar 状态。
 * @param text 待复制的文本。
 */
private fun copyToClipboard(
    clipboardManager: ClipboardManager,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    text: String,
) {
    clipboardManager.setText(AnnotatedString(text))
    scope.launch {
        snackbarHostState.showSnackbar("已复制")
    }
}
