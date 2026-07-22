package com.radium.skylark.converter.mapper

import com.radium.skylark.converter.model.ProxyNode
import com.radium.skylark.converter.model.TlsOptions
import com.radium.skylark.converter.model.Transport
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * 将 [ProxyNode] 映射为 sing-box outbound（JSON 对象）。
 */
object OutboundMapper {

    fun toOutbound(node: ProxyNode): JsonObject = when (node) {
        is ProxyNode.Shadowsocks -> buildJsonObject {
            put("type", "shadowsocks")
            put("tag", node.tag)
            put("server", node.server)
            put("server_port", node.serverPort)
            put("method", node.method)
            put("password", node.password)
            node.plugin?.let { put("plugin", it) }
            node.pluginOpts?.let { put("plugin_opts", it) }
        }

        is ProxyNode.Vmess -> buildJsonObject {
            put("type", "vmess")
            put("tag", node.tag)
            put("server", node.server)
            put("server_port", node.serverPort)
            put("uuid", node.uuid)
            put("security", node.security)
            put("alter_id", node.alterId)
            putTls(node.tls)
            putTransport(node.transport)
        }

        is ProxyNode.Vless -> buildJsonObject {
            put("type", "vless")
            put("tag", node.tag)
            put("server", node.server)
            put("server_port", node.serverPort)
            put("uuid", node.uuid)
            node.flow?.takeIf { it.isNotBlank() }?.let { put("flow", it) }
            putTls(node.tls)
            putTransport(node.transport)
        }

        is ProxyNode.Trojan -> buildJsonObject {
            put("type", "trojan")
            put("tag", node.tag)
            put("server", node.server)
            put("server_port", node.serverPort)
            put("password", node.password)
            putTls(node.tls)
            putTransport(node.transport)
        }

        is ProxyNode.Hysteria2 -> buildJsonObject {
            put("type", "hysteria2")
            put("tag", node.tag)
            put("server", node.server)
            put("server_port", node.serverPort)
            put("password", node.password)
            node.obfsPassword?.let {
                putJsonObject("obfs") {
                    put("type", "salamander")
                    put("password", it)
                }
            }
            putTls(node.tls)
        }

        is ProxyNode.Tuic -> buildJsonObject {
            put("type", "tuic")
            put("tag", node.tag)
            put("server", node.server)
            put("server_port", node.serverPort)
            put("uuid", node.uuid)
            put("password", node.password)
            put("congestion_control", node.congestionControl)
            putTls(node.tls)
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putTls(tls: TlsOptions?) {
        if (tls == null || !tls.enabled) return
        putJsonObject("tls") {
            put("enabled", true)
            tls.serverName?.takeIf { it.isNotBlank() }?.let { put("server_name", it) }
            if (tls.insecure) put("insecure", true)
            if (tls.alpn.isNotEmpty()) putJsonArray("alpn") { tls.alpn.forEach { add(it) } }
            tls.fingerprint?.takeIf { it.isNotBlank() }?.let {
                putJsonObject("utls") {
                    put("enabled", true)
                    put("fingerprint", it)
                }
            }
            if (!tls.realityPublicKey.isNullOrBlank()) {
                putJsonObject("reality") {
                    put("enabled", true)
                    put("public_key", tls.realityPublicKey)
                    tls.realityShortId?.let { sid -> put("short_id", sid) }
                }
            }
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putTransport(transport: Transport) {
        when (transport) {
            is Transport.Tcp -> Unit
            is Transport.Ws -> putJsonObject("transport") {
                put("type", "ws")
                put("path", transport.path)
                val headers = buildMap {
                    transport.host?.let { put("Host", it) }
                    putAll(transport.headers)
                }
                if (headers.isNotEmpty()) putJsonObject("headers") {
                    headers.forEach { (k, v) -> put(k, v) }
                }
            }

            is Transport.Grpc -> putJsonObject("transport") {
                put("type", "grpc")
                put("service_name", transport.serviceName)
            }

            is Transport.HttpUpgrade -> putJsonObject("transport") {
                put("type", "httpupgrade")
                put("path", transport.path)
                transport.host?.let { put("host", it) }
            }
        }
    }
}
