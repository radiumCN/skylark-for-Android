package com.radium.skylark.converter.base64

import java.util.Base64

/**
 * Base64 聚合订阅解析：许多订阅将「分享链接列表」整体做 Base64 编码。
 */
object Base64SubParser {

    private val base64Regex = Regex("^[A-Za-z0-9+/_\\-\\s]+={0,2}$")

    /** 粗略判断是否像整体 Base64（无明显协议前缀、字符集合法、长度足够）。 */
    fun isLikelyBase64(text: String): Boolean {
        val t = text.trim()
        if (t.length < 16) return false
        if (t.contains("://")) return false
        if (t.contains("proxies:")) return false
        return base64Regex.matches(t)
    }

    /** 解码为文本；失败返回 null。 */
    fun decode(text: String): String? {
        val s = text.trim().replace("\n", "").replace("\r", "")
            .replace('-', '+').replace('_', '/')
        val padded = when (s.length % 4) {
            2 -> "$s=="
            3 -> "$s="
            else -> s
        }
        return runCatching { String(Base64.getDecoder().decode(padded)) }.getOrNull()
            ?: runCatching { String(Base64.getMimeDecoder().decode(padded)) }.getOrNull()
    }
}
