package com.myagent.app.network

import com.myagent.app.network.dto.ChatCompletionChunk
import com.myagent.app.network.dto.ChatCompletionResponse
import com.myagent.app.network.dto.ChatMessage
import com.myagent.app.network.dto.ChatRequest
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource

private val JSON_MEDIA_TYPE = "application/json".toMediaType()

/**
 * OpenAI 兼容 `/chat/completions` 客户端。
 *
 * 支持 SSE 流式增量返回；若服务端忽略 stream 参数直接返回完整 JSON，
 * 也会解析后一次性发出全部文本。
 *
 * @param client 底层 OkHttp 客户端，默认已配置流式读取不超时。
 */
class OpenAiClient(private val client: OkHttpClient = defaultClient()) : ChatApi {

    private val json = Json {
        ignoreUnknownKeys = true
        // 确保 stream=true 被写入请求体，而不是作为默认值被省略。
        encodeDefaults = true
    }

    /**
     * 发起一次聊天请求，并逐段返回模型输出文本。
     *
     * 调用方取消收集即可停止生成；SSE 的每个非空 delta 会立即发出。
     *
     * @param baseUrl 服务地址，末尾斜杠可有可无。
     * @param apiKey API Key，会以 Bearer 方式放入请求头。
     * @param model 模型名。
     * @param messages 本次请求的完整消息列表。
     * @param temperature 采样温度。
     * @return 增量文本流。
     * @throws ApiException 网络、HTTP 或解析错误。
     */
    override fun chatStream(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        temperature: Double,
    ): Flow<String> = flow {
        coroutineScope {
            // 阻塞读取放在 IO 协程中，通过 Channel 把增量送回 Flow 的收集上下文。
            val chunks = Channel<String>(Channel.UNLIMITED)
            val producer = launch {
                try {
                    streamInto(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        model = model,
                        messages = messages,
                        temperature = temperature,
                        chunks = chunks,
                    )
                    chunks.close()
                } catch (e: CancellationException) {
                    chunks.cancel(e)
                    throw e
                } catch (e: Exception) {
                    chunks.close(e)
                }
            }
            try {
                for (chunk in chunks) {
                    emit(chunk)
                }
            } finally {
                producer.cancel()
            }
        }
    }

    private suspend fun streamInto(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        temperature: Double,
        chunks: SendChannel<String>,
    ) {
        withContext(Dispatchers.IO) {
            try {
                val request = buildRequest(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    model = model,
                    messages = messages,
                    temperature = temperature,
                )
                val call = client.newCall(request)
                // 取消收集时主动取消 OkHttp 调用，避免阻塞在未设置超时的读取上。
                val cancelWatcher = launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        awaitCancellation()
                    } finally {
                        call.cancel()
                    }
                }
                try {
                    call.execute().use { response ->
                        if (!response.isSuccessful) {
                            val errorBody = response.body.string().take(200)
                            throw mapHttpError(response.code, errorBody)
                        }
                        val contentType = response.header("Content-Type").orEmpty()
                        if (contentType.contains("text/event-stream", ignoreCase = true)) {
                            readSse(response.body.source(), chunks)
                        } else {
                            emitWholeResponse(response.body.string(), chunks)
                        }
                    }
                } finally {
                    cancelWatcher.cancel()
                }
            } catch (e: ApiException) {
                throw e
            } catch (e: SerializationException) {
                throw ApiException(Kind.PARSE, e.message ?: ApiException.userMessage(Kind.PARSE))
            } catch (e: IOException) {
                throw ApiException(Kind.NETWORK, e.message ?: ApiException.userMessage(Kind.NETWORK))
            }
        }
    }

    private suspend fun readSse(source: BufferedSource, chunks: SendChannel<String>) {
        while (true) {
            currentCoroutineContext().ensureActive()
            val line = source.readUtf8Line() ?: break
            if (SseParser.isDone(line)) {
                break
            }
            val data = SseParser.extractData(line) ?: continue
            val chunk = try {
                json.decodeFromString<ChatCompletionChunk>(data)
            } catch (e: SerializationException) {
                continue
            }
            val content = chunk.choices.firstOrNull()?.delta?.content
            if (!content.isNullOrEmpty()) {
                chunks.send(content)
            }
        }
    }

    private suspend fun emitWholeResponse(body: String, chunks: SendChannel<String>) {
        val response = json.decodeFromString<ChatCompletionResponse>(body)
        val content = response.choices.firstOrNull()?.message?.content
        if (!content.isNullOrEmpty()) {
            chunks.send(content)
        }
    }

    private fun buildRequest(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        temperature: Double,
    ): Request {
        val url = baseUrl.trimEnd('/') + "/chat/completions"
        val payload = json.encodeToString(
            ChatRequest(
                model = model,
                messages = messages,
                stream = true,
                temperature = temperature,
            ),
        )
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun mapHttpError(code: Int, body: String): ApiException {
        val kind = when {
            code == 401 -> Kind.UNAUTHORIZED
            code == 404 -> Kind.NOT_FOUND
            code == 429 -> Kind.RATE_LIMITED
            code >= 500 -> Kind.SERVER
            else -> Kind.HTTP
        }
        return ApiException(kind, "HTTP $code: $body")
    }

    companion object {
        /**
         * 创建默认 OkHttp 客户端：连接与写入超时 20 秒，流式读取不设超时。
         */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }
}
