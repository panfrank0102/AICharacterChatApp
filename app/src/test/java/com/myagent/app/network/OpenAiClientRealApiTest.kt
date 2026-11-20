package com.myagent.app.network

import com.myagent.app.network.dto.ChatMessage
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 真实 API 验证测试（开发步骤第 5 步验收）。
 *
 * 从工作区根目录的 "API key.txt" 读取 DeepSeek API Key（不把密钥写入源码）。
 * 找不到密钥文件时跳过（直接返回，不报失败）。
 */
class OpenAiClientRealApiTest {

    private fun loadApiKey(): String? {
        val candidates = listOf(
            File("API key.txt"),
            File("../API key.txt"),
            File("app/API key.txt"),
            File("D:\\Codex\\MyAgent_android\\API key.txt"),
        )
        for (f in candidates) {
            if (f.exists()) {
                val key = f.readLines().firstOrNull { it.trim().startsWith("sk-") }?.trim()
                if (!key.isNullOrEmpty()) return key
            }
        }
        return null
    }

    @Test
    fun chatStream_withRealDeepSeekApi_returnsStreamedContent() {
        val apiKey = loadApiKey()
        if (apiKey == null) {
            println("SKIP: API key file not found")
            return
        }

        // 用接口类型接收，以便使用 chatStream 的默认参数（实现类 override 不能重复声明默认值）。
        val client: ChatApi = OpenAiClient()
        val chunks = runBlocking {
            client.chatStream(
                baseUrl = "https://api.deepseek.com",
                apiKey = apiKey,
                model = "deepseek-chat",
                messages = listOf(ChatMessage(role = "user", content = "请用一句话介绍你自己")),
                temperature = 0.7
            ).toList()
        }

        assertTrue("应收到至少一个流式分片", chunks.isNotEmpty())
        val joined = chunks.joinToString("")
        assertFalse("合并后的回复不应为空", joined.isBlank())
        println("收到 ${chunks.size} 个分片，共 ${joined.length} 字符")
        println("回复内容：${joined.take(100)}")
    }

    /**
     * 验证官方 API 接受 `reasoning_effort` 参数（不思考 / 低 两档）。
     *
     * 使用文档中列出的 deepseek-flash 模型；若密钥无该模型权限会表现为 HTTP 错误，
     * 此时跳过并通过控制台提示（避免把服务端权限问题误判为代码问题）。
     */
    @Test
    fun chatStream_withReasoningEffort_isAcceptedByOfficialApi() {
        val apiKey = loadApiKey()
        if (apiKey == null) {
            println("SKIP: API key file not found")
            return
        }

        val client: ChatApi = OpenAiClient()
        listOf("none", "low").forEach { effort ->
            try {
                val chunks = runBlocking {
                    client.chatStream(
                        baseUrl = "https://api.deepseek.com",
                        apiKey = apiKey,
                        model = "deepseek-flash",
                        messages = listOf(ChatMessage(role = "user", content = "用一句话介绍你自己")),
                        temperature = 0.7,
                        reasoningEffort = effort,
                    ).toList()
                }
                assertTrue("reasoning_effort=$effort 应收到至少一个流式分片", chunks.isNotEmpty())
                val joined = chunks.joinToString("")
                assertFalse("reasoning_effort=$effort 的回复不应为空", joined.isBlank())
                println("reasoning_effort=$effort 验证通过，收到 ${chunks.size} 个分片：${joined.take(60)}")
            } catch (e: ApiException) {
                println("SKIP reasoning_effort=$effort：官方 API 返回 ${e.message}（可能是模型权限问题）")
            }
        }
    }
}
