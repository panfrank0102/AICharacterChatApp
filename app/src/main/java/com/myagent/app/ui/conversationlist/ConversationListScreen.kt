package com.myagent.app.ui.conversationlist

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myagent.app.data.db.entity.ConversationEntity
import com.myagent.app.domain.import.CharacterCardParser
import com.myagent.app.ui.cardimport.ImportState
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.launch

/**
 * 会话列表页：展示全部会话，支持新建、进入会话、长按重命名/删除，并提供角色卡导入与全局设置入口。
 *
 * @param onOpenChat 打开指定会话的聊天页。
 * @param onOpenSettings 打开全局设置页。
 * @param onImportCard 角色卡解析成功后跳转导入预览页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onOpenChat: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onImportCard: () -> Unit,
) {
    val vm: ConversationListViewModel = viewModel()
    val conversations by vm.conversations.collectAsState()
    val scope = rememberCoroutineScope()
    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm") }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 通过系统文件选择器读取角色卡（PNG 内嵌卡数据或 JSON 文件），解析成功后暂存卡片并跳转预览页。
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
                }.getOrNull()
                if (bytes == null) {
                    snackbarHostState.showSnackbar("读取文件失败")
                    return@launch
                }
                val card = runCatching { CharacterCardParser.parse(bytes) }.getOrElse { e ->
                    snackbarHostState.showSnackbar(e.message ?: "角色卡解析失败")
                    return@launch
                }
                ImportState.pendingCard = card
                onImportCard()
            }
        }
    }

    var managedConversation by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameConversation by remember { mutableStateOf<ConversationEntity?>(null) }
    var deleteConversation by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MyAgent") },
                actions = {
                    TextButton(
                        onClick = { importLauncher.launch(arrayOf("image/png", "application/json")) },
                    ) {
                        Text("导入")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        val id = vm.createConversation()
                        onOpenChat(id)
                    }
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建会话")
            }
        },
    ) { innerPadding ->
        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("暂无会话，点击右下角 + 新建")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = conversations, key = { it.id }) { conversation ->
                    ConversationListItem(
                        conversation = conversation,
                        updatedAtText = timeFormat.format(Date(conversation.updatedAt)),
                        onClick = { onOpenChat(conversation.id) },
                        onLongClick = { managedConversation = conversation },
                    )
                }
            }
        }
    }

    managedConversation?.let { conversation ->
        AlertDialog(
            onDismissRequest = { managedConversation = null },
            title = { Text(conversation.title.ifBlank { "新会话" }) },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = {
                            renameText = conversation.title
                            renameConversation = conversation
                            managedConversation = null
                        },
                    ) {
                        Text("重命名")
                    }
                    TextButton(
                        onClick = {
                            deleteConversation = conversation
                            managedConversation = null
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("删除")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { managedConversation = null }) {
                    Text("取消")
                }
            },
        )
    }

    renameConversation?.let { conversation ->
        AlertDialog(
            onDismissRequest = { renameConversation = null },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("会话标题") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.isNotBlank(),
                    onClick = {
                        val newTitle = renameText.trim()
                        if (newTitle.isNotEmpty()) {
                            vm.rename(conversation.id, newTitle)
                        }
                        renameConversation = null
                    },
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameConversation = null }) {
                    Text("取消")
                }
            },
        )
    }

    deleteConversation?.let { conversation ->
        AlertDialog(
            onDismissRequest = { deleteConversation = null },
            title = { Text("删除会话") },
            text = { Text("删除该会话？聊天记录与世界书将一并删除") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.delete(conversation.id)
                        deleteConversation = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConversation = null }) {
                    Text("取消")
                }
            },
        )
    }
}

/**
 * 单个会话卡片：上方为标题，下方为更新时间；单击进入会话，长按弹出管理对话框。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationListItem(
    conversation: ConversationEntity,
    updatedAtText: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = conversation.title.ifBlank { "新会话" },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = updatedAtText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
