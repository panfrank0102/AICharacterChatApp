package com.myagent.app.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myagent.app.data.db.entity.ConversationEntity
import com.myagent.app.data.db.entity.MessageEntity
import com.myagent.app.data.repository.ChatRepository
import com.myagent.app.data.repository.ConversationRepository
import com.myagent.app.data.repository.MessageRepository
import com.myagent.app.network.ApiException
import com.myagent.app.ui.AppGraph
import com.myagent.app.ui.navigation.Routes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 聊天页 ViewModel：持有当前会话的消息、会话信息与流式输出状态。
 *
 * 会话 id 从导航参数 [Routes.ARG_CONVERSATION_ID] 中读取；消息与会话信息直接观察仓库
 * 的 Flow，发送、重新生成、停止等操作委托给 [ChatRepository]。
 *
 * @param savedStateHandle 导航传入的状态句柄，必须包含会话 id。
 * @param chatRepository 聊天编排仓库。
 * @param messageRepository 消息仓库。
 * @param conversationRepository 会话仓库。
 */
class ChatViewModel(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository = AppGraph.chatRepository,
    private val messageRepository: MessageRepository = AppGraph.messageRepository,
    private val conversationRepository: ConversationRepository = AppGraph.conversationRepository,
) : ViewModel() {

    /** 当前会话 id。 */
    private val conversationId: Long =
        savedStateHandle.get<Long>(Routes.ARG_CONVERSATION_ID) ?: error("缺少会话 id")

    /** 当前会话的全部消息（按 id 升序）。 */
    val messages: StateFlow<List<MessageEntity>> =
        messageRepository.observeByConversation(conversationId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 当前会话信息；尚未加载完成时为 null。 */
    val conversation: StateFlow<ConversationEntity?> =
        conversationRepository.observeById(conversationId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 输入框内容。 */
    val inputText = MutableStateFlow("")

    /** 是否正在流式接收 AI 回复。 */
    val streaming = MutableStateFlow(false)

    /** 本轮流式输出已接收的增量文本。 */
    val streamingText = MutableStateFlow("")

    /** 待展示的错误提示；界面展示后调用 [clearError] 清除。 */
    val error = MutableStateFlow<String?>(null)

    /** 当前流式请求任务，用于「停止」时取消。 */
    private var streamJob: Job? = null

    /** 发送输入框内容；正在流式输出或输入内容为空白时忽略。 */
    fun send() {
        if (streaming.value) return
        val text = inputText.value.trim()
        if (text.isEmpty()) return
        inputText.value = ""
        launchRequest { chatRepository.sendMessage(conversationId, text) }
    }

    /** 重新生成最后一条 AI 回复；正在流式输出时忽略。 */
    fun regenerate() {
        if (streaming.value) return
        launchRequest { chatRepository.regenerate(conversationId) }
    }

    /** 重试：重新生成即重试（失败时最后一条是用户消息，重新生成会再次请求）。 */
    fun retry() = regenerate()

    /** 停止当前生成；已生成的部分内容由聊天仓库负责落库。 */
    fun stop() {
        streamJob?.cancel()
        streamJob = null
    }

    /** 删除指定消息及其之后的所有消息。 */
    fun deleteFrom(messageId: Long) {
        viewModelScope.launch {
            messageRepository.deleteFrom(conversationId, messageId)
        }
    }

    /** 清除错误提示（snackbar 展示结束后调用）。 */
    fun clearError() {
        error.value = null
    }

    /**
     * 启动一次流式请求：把增量文本累加到 [streamingText]，失败时写入 [error]。
     *
     * 请求取消（用户点击「停止」）会被重新抛出，以便协程正常结束；其余异常统一转成中文提示。
     *
     * @param block 返回增量文本 Flow 的挂起函数。
     */
    private fun launchRequest(block: suspend () -> Flow<String>) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            streaming.value = true
            streamingText.value = ""
            error.value = null
            try {
                block().collect { delta ->
                    streamingText.update { it + delta }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = toUserMessage(e)
            } finally {
                streaming.value = false
                streamingText.value = ""
            }
        }
    }

    /** 把异常转换成面向用户的中文提示。 */
    private fun toUserMessage(e: Throwable): String = when (e) {
        is ApiException -> ApiException.userMessage(e.kind)
        is IllegalStateException -> e.message ?: "操作失败"
        else -> "发生未知错误"
    }
}
