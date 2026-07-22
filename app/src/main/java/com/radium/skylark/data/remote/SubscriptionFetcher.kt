package com.radium.skylark.data.remote

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 通过 HTTP(S) 拉取订阅文本内容。
 */
@Singleton
class SubscriptionFetcher @Inject constructor(
    private val client: OkHttpClient,
) {
    /**
     * 拉取订阅内容。
     * @param userAgent 部分订阅服务按 UA 返回不同格式（如 clash / clash-verge）。
     */
    suspend fun fetch(url: String, userAgent: String = "clash-verge"): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }
                response.body?.string() ?: throw IOException("Empty response body")
            }
        }
}
