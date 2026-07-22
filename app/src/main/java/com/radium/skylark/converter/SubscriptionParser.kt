package com.radium.skylark.converter

import com.radium.skylark.converter.base64.Base64SubParser
import com.radium.skylark.converter.clash.ClashYamlParser
import com.radium.skylark.converter.link.ShareLinkParser
import com.radium.skylark.converter.model.ParsedSubscription

/**
 * 订阅解析总入口：自动探测订阅类型并分发到对应解析器。
 *
 * 支持：Clash / Clash.Meta YAML、分享链接列表、Base64 聚合订阅。
 * （sing-box JSON 直接复用 outbounds，不经由 ProxyNode，另行处理。）
 */
object SubscriptionParser {

    enum class Format { CLASH_YAML, SHARE_LINKS, BASE64, UNKNOWN }

    fun detect(raw: String): Format {
        val text = raw.trim()
        return when {
            ClashYamlParser.isClashYaml(text) -> Format.CLASH_YAML
            text.contains("://") -> Format.SHARE_LINKS
            Base64SubParser.isLikelyBase64(text) -> Format.BASE64
            else -> Format.UNKNOWN
        }
    }

    fun parse(raw: String): ParsedSubscription {
        val text = raw.trim()
        return when (detect(text)) {
            Format.CLASH_YAML -> ClashYamlParser.parse(text)
            Format.SHARE_LINKS -> ParsedSubscription(ShareLinkParser.parseMany(text))
            Format.BASE64 -> {
                val decoded = Base64SubParser.decode(text) ?: return ParsedSubscription.EMPTY
                // 解码后可能是分享链接列表或 Clash YAML，递归判定
                when (detect(decoded)) {
                    Format.CLASH_YAML -> ClashYamlParser.parse(decoded)
                    else -> ParsedSubscription(ShareLinkParser.parseMany(decoded))
                }
            }

            Format.UNKNOWN -> ParsedSubscription.EMPTY
        }
    }
}
