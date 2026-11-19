package com.myagent.app.ui.sessionsettings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.myagent.app.data.db.entity.WorldBookEntryEntity

/** 关键词预览的最大字符数。 */
private const val KEYWORD_PREVIEW_LENGTH = 30

/**
 * 世界书列表页。
 *
 * 展示当前会话的世界书条目（标题、关键词预览、优先级、启用开关），
 * 支持点击条目编辑、切换启用状态、确认后删除，以及通过右下角按钮新建条目。
 *
 * @param onBack 返回上一页。
 * @param onAddEntry 打开新建条目页。
 * @param onEditEntry 打开指定条目的编辑页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldbookListScreen(
    onBack: () -> Unit,
    onAddEntry: () -> Unit,
    onEditEntry: (Long) -> Unit,
) {
    val vm: WorldbookListViewModel = viewModel(
        factory = viewModelFactory {
            initializer { WorldbookListViewModel(createSavedStateHandle()) }
        },
    )
    val entries by vm.entries.collectAsState()
    var deleteTarget by remember { mutableStateOf<WorldBookEntryEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("世界书") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddEntry) {
                Icon(Icons.Default.Add, contentDescription = "新建条目")
            }
        },
    ) { innerPadding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无条目，点击 + 新建",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries, key = { entry -> entry.id }) { entry ->
                    WorldbookEntryCard(
                        entry = entry,
                        onOpen = { onEditEntry(entry.id) },
                        onToggle = { vm.toggleEnabled(entry) },
                        onDeleteRequest = { deleteTarget = entry },
                    )
                }
            }
        }
    }

    val target = deleteTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除条目") },
            text = { Text("确定要删除“${target.title}”吗？删除后不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        vm.delete(target.id)
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("取消")
                }
            },
        )
    }
}

/** 世界书列表中的单张条目卡片。 */
@Composable
private fun WorldbookEntryCard(
    entry: WorldBookEntryEntity,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Switch(
                    checked = entry.enabled,
                    onCheckedChange = { onToggle() },
                )
            }
            Spacer(Modifier.height(4.dp))
            val keywordPreview = buildKeywordPreview(entry.keywords)
            Text(
                text = if (keywordPreview.isBlank()) "关键词：未设置" else "关键词：$keywordPreview",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "优先级 ${entry.priority}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDeleteRequest) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** 把换行分隔的关键词转换为顿号分隔的预览文本，并截断到固定长度。 */
private fun buildKeywordPreview(raw: String): String {
    val preview = raw.replace('\n', '、')
    return if (preview.length <= KEYWORD_PREVIEW_LENGTH) {
        preview
    } else {
        preview.take(KEYWORD_PREVIEW_LENGTH) + "…"
    }
}
