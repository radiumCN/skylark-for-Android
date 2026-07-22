package com.radium.skylark.converter

import com.radium.skylark.converter.mapper.OutboundMapper
import com.radium.skylark.converter.model.ParsedSubscription
import com.radium.skylark.converter.model.ProxyGroup
import com.radium.skylark.converter.model.ProxyNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * 将一组 [ProxyNode] 拼装为完整的 sing-box `config.json`。
 *
 * 输出包含：tun + mixed 入站、节点出站、Auto(urltest) 自动选择组、
 * proxy(selector) 手动选择组、direct、以及基础 route / dns。
 */
object ConfigBuilder {

    private val prettyJson = Json { prettyPrint = true }

    data class Options(
        val mixedPort: Int = 2080,
        val autoTestUrl: String = "https://www.gstatic.com/generate_204",
        val autoTestInterval: String = "300s",
        val autoTestTolerance: Int = 50,
        val autoGroupTag: String = "Auto",
        val selectorTag: String = "proxy",
        val logLevel: String = "info",
    )

    const val AUTO_GROUP_TAG = "Auto"
    const val SELECTOR_TAG = "proxy"

    fun build(nodes: List<ProxyNode>, options: Options = Options()): JsonObject {
        val nodeTags = nodes.map { it.tag }
        return assemble(options, finalTag = options.selectorTag) {
            putOutbounds(nodes, nodeTags, options)
        }
    }

    /**
     * 使用订阅中的 proxy-groups 生成 selector/urltest。
     * 若订阅无组，则回退到默认的 Auto + proxy 两个组。
     */
    fun build(subscription: ParsedSubscription, options: Options = Options()): JsonObject {
        if (subscription.groups.isEmpty()) return build(subscription.nodes, options)
        val primary = subscription.groups.firstOrNull { it.type == ProxyGroup.GroupType.SELECT }
            ?: subscription.groups.first()
        return assemble(options, finalTag = primary.name) {
            putOutboundsFromGroups(subscription, options)
        }
    }

    fun buildJsonString(nodes: List<ProxyNode>, options: Options = Options()): String =
        prettyJson.encodeToString(JsonObject.serializer(), build(nodes, options))

    fun buildJsonString(subscription: ParsedSubscription, options: Options = Options()): String =
        prettyJson.encodeToString(JsonObject.serializer(), build(subscription, options))

    private fun assemble(
        options: Options,
        finalTag: String,
        outboundsBlock: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ): JsonObject = buildJsonObject {
        putJsonObject("log") {
            put("level", options.logLevel)
            put("timestamp", true)
        }
        putDns(finalTag)
        putInbounds(options)
        outboundsBlock()
        putRoute(finalTag)
        putJsonObject("experimental") {
            putJsonObject("clash_api") {
                put("external_controller", "127.0.0.1:9090")
            }
            putJsonObject("cache_file") {
                put("enabled", true)
            }
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putDns(proxyTag: String) {
        putJsonObject("dns") {
            putJsonArray("servers") {
                addJsonObject {
                    put("tag", "remote")
                    put("address", "https://1.1.1.1/dns-query")
                    put("detour", proxyTag)
                }
                addJsonObject {
                    put("tag", "local")
                    put("address", "https://223.5.5.5/dns-query")
                    put("detour", "direct")
                }
            }
            put("final", "remote")
            put("strategy", "prefer_ipv4")
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putInbounds(options: Options) {
        putJsonArray("inbounds") {
            addJsonObject {
                put("type", "tun")
                put("tag", "tun-in")
                put("stack", "gvisor")
                put("auto_route", true)
                put("strict_route", true)
                putJsonArray("address") { add("172.19.0.1/30") }
            }
            addJsonObject {
                put("type", "mixed")
                put("tag", "mixed-in")
                put("listen", "127.0.0.1")
                put("listen_port", options.mixedPort)
            }
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putOutbounds(
        nodes: List<ProxyNode>,
        nodeTags: List<String>,
        options: Options,
    ) {
        putJsonArray("outbounds") {
            // 手动选择组：Auto + 所有节点 + direct
            addJsonObject {
                put("type", "selector")
                put("tag", options.selectorTag)
                putJsonArray("outbounds") {
                    if (nodeTags.isNotEmpty()) add(options.autoGroupTag)
                    nodeTags.forEach { add(it) }
                    add("direct")
                }
                put("default", if (nodeTags.isNotEmpty()) options.autoGroupTag else "direct")
            }
            // Auto 自动测速组（仅当有节点时）
            if (nodeTags.isNotEmpty()) {
                addJsonObject {
                    put("type", "urltest")
                    put("tag", options.autoGroupTag)
                    putJsonArray("outbounds") { nodeTags.forEach { add(it) } }
                    put("url", options.autoTestUrl)
                    put("interval", options.autoTestInterval)
                    put("tolerance", options.autoTestTolerance)
                }
            }
            // 各节点出站
            nodes.forEach { add(OutboundMapper.toOutbound(it)) }
            // 基础出站
            addJsonObject {
                put("type", "direct")
                put("tag", "direct")
            }
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putRoute(finalTag: String) {
        putJsonObject("route") {
            putJsonArray("rules") {
                addJsonObject {
                    put("action", "sniff")
                }
                addJsonObject {
                    put("protocol", "dns")
                    put("action", "hijack-dns")
                }
                addJsonObject {
                    put("ip_is_private", true)
                    put("outbound", "direct")
                }
            }
            put("final", finalTag)
            put("auto_detect_interface", true)
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putOutboundsFromGroups(
        subscription: ParsedSubscription,
        options: Options,
    ) {
        val nodeTags = subscription.nodes.map { it.tag }.toSet()
        val groupNames = subscription.groups.map { it.name }.toSet()
        val knownTags = nodeTags + groupNames + "direct" + "block"
        var needBlock = false

        fun resolveMember(member: String): String? = when (member.uppercase()) {
            "DIRECT" -> "direct"
            "REJECT" -> "block".also { needBlock = true }
            else -> member.takeIf { it in knownTags }
        }

        putJsonArray("outbounds") {
            // 各代理组
            subscription.groups.forEach { group ->
                val members = group.members.mapNotNull { resolveMember(it) }
                when (group.type) {
                    ProxyGroup.GroupType.SELECT -> addJsonObject {
                        put("type", "selector")
                        put("tag", group.name)
                        putJsonArray("outbounds") {
                            (members.ifEmpty { listOf("direct") }).forEach { add(it) }
                        }
                        put("default", members.firstOrNull() ?: "direct")
                    }

                    ProxyGroup.GroupType.URLTEST -> if (members.isNotEmpty()) addJsonObject {
                        put("type", "urltest")
                        put("tag", group.name)
                        putJsonArray("outbounds") { members.forEach { add(it) } }
                        put("url", group.testUrl ?: options.autoTestUrl)
                        put("interval", group.intervalSeconds?.let { "${it}s" } ?: options.autoTestInterval)
                        put("tolerance", group.tolerance ?: options.autoTestTolerance)
                    }
                }
            }
            // 各节点出站
            subscription.nodes.forEach { add(OutboundMapper.toOutbound(it)) }
            // 基础出站
            addJsonObject {
                put("type", "direct")
                put("tag", "direct")
            }
            if (needBlock) addJsonObject {
                put("type", "block")
                put("tag", "block")
            }
        }
    }
}
