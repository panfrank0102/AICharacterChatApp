package com.myagent.app.network

/**
 * 网络层统一异常，携带便于界面展示的错误分类。
 *
 * @property kind 错误分类。
 */
class ApiException(
    val kind: Kind,
    message: String,
) : Exception(message) {

    companion object {
        /**
         * 返回面向用户的中文错误提示。
         */
        fun userMessage(kind: Kind): String = when (kind) {
            Kind.NETWORK -> "网络连接失败，请检查网络或服务地址"
            Kind.UNAUTHORIZED -> "API Key 无效或服务地址错误"
            Kind.NOT_FOUND -> "服务地址或模型名错误"
            Kind.RATE_LIMITED -> "请求过于频繁，请稍后重试"
            Kind.SERVER -> "模型服务端错误，请稍后重试"
            Kind.HTTP -> "请求失败（HTTP 错误）"
            Kind.PARSE -> "响应解析失败"
        }
    }
}

/**
 * 网络请求错误分类。
 */
enum class Kind {
    NETWORK,
    UNAUTHORIZED,
    NOT_FOUND,
    RATE_LIMITED,
    SERVER,
    HTTP,
    PARSE,
}
