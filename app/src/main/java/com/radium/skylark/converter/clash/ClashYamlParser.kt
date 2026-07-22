package com.radium.skylark.converter.clash

import com.radium.skylark.converter.model.ParsedSubscription
import com.radium.skylark.converter.model.ProxyGroup
import com.radium.skylark.converter.model.ProxyNode
import com.radium.skylark.converter.model.TlsOptions
import com.radium.skylark.converter.model.Transport
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * 解析 Clash / Clash.Meta YAML 订阅（`proxies:` + `proxy-groups:`）为 [ParsedSubscription]。
 *
 * 使用 SnakeYAML 的 [SafeConstructor]，只构造基础数据类型，避免任意对象实例化。
 * 未知/不支持的节点类型会被跳过。
 */
object ClashYamlParser {

    fun isClashYaml(text: String): Boolean {
        val t = text.trimStart()
        if (t.startsWith("{") || t.startsWith("[")) return false
        return t.contains("proxies:")
    }

    fun parse(text: String): ParsedSubscription {
        val yaml = Yaml(SafeConstructor(LoaderOptions()))
        @Suppress("UNCHECKED_CAST")
        val root = runCatching { yaml.load<Any?>(text) as? Map<String, Any?> }.getOrNull()
            ?: return ParsedSubscription.EMPTY

        val proxies = (root["proxies"] as? List<*>).orEmpty()
        val nodes = proxies.mapNotNull { raw ->
            (raw as? Map<*, *>)?.let { runCatching { parseProxy(Node(it)) }.getOrNull() }
        }

        val groups = (root["proxy-groups"] as? List<*>).orEmpty().mapNotNull { raw ->
            (raw as? Map<*, *>)?.let { runCatching { parseGroup(Node(it)) }.getOrNull() }
        }

        return ParsedSubscription(nodes, groups)
    }

    private fun parseProxy(n: Node): ProxyNode? {
        val name = n.str("name") ?: return null
        val server = n.str("server") ?: return null
        val port = n.int("port") ?: return null
        return when (n.str("type")?.lowercase()) {
            "ss", "shadowsocks" -> ProxyNode.Shadowsocks(
                tag = name, server = server, serverPort = port,
                method = n.str("cipher") ?: return null,
                password = n.str("password") ?: return null,
                plugin = n.str("plugin"),
            )

            "vmess" -> ProxyNode.Vmess(
                tag = name, server = server, serverPort = port,
                uuid = n.str("uuid") ?: return null,
                alterId = n.int("alterId") ?: 0,
                security = n.str("cipher") ?: "auto",
                tls = tlsIf(n.bool("tls") == true, n),
                transport = transport(n),
            )

            "vless" -> ProxyNode.Vless(
                tag = name, server = server, serverPort = port,
                uuid = n.str("uuid") ?: return null,
                flow = n.str("flow"),
                tls = tlsIf(n.bool("tls") == true || n.map("reality-opts") != null, n),
                transport = transport(n),
            )

            "trojan" -> ProxyNode.Trojan(
                tag = name, server = server, serverPort = port,
                password = n.str("password") ?: return null,
                tls = tlsIf(true, n),
                transport = transport(n),
            )

            "hysteria2", "hy2" -> ProxyNode.Hysteria2(
                tag = name, server = server, serverPort = port,
                password = n.str("password") ?: return null,
                obfsPassword = n.str("obfs-password"),
                tls = tlsIf(true, n),
            )

            "tuic" -> ProxyNode.Tuic(
                tag = name, server = server, serverPort = port,
                uuid = n.str("uuid") ?: return null,
                password = n.str("password") ?: "",
                congestionControl = n.str("congestion-controller") ?: "bbr",
                tls = tlsIf(true, n),
            )

            else -> null
        }
    }

    private fun tlsIf(enabled: Boolean, n: Node): TlsOptions? {
        if (!enabled) return null
        val reality = n.map("reality-opts")
        return TlsOptions(
            enabled = true,
            serverName = n.str("servername") ?: n.str("sni"),
            insecure = n.bool("skip-cert-verify") == true,
            alpn = n.strList("alpn"),
            fingerprint = n.str("client-fingerprint"),
            realityPublicKey = reality?.let { Node(it).str("public-key") },
            realityShortId = reality?.let { Node(it).str("short-id") },
        )
    }

    private fun transport(n: Node): Transport = when (n.str("network")?.lowercase()) {
        "ws" -> {
            val ws = n.map("ws-opts")?.let { Node(it) }
            val headers = ws?.map("headers")?.let { Node(it) }
            Transport.Ws(
                path = ws?.str("path") ?: "/",
                host = headers?.str("Host") ?: headers?.str("host"),
            )
        }

        "grpc" -> {
            val grpc = n.map("grpc-opts")?.let { Node(it) }
            Transport.Grpc(serviceName = grpc?.str("grpc-service-name") ?: "")
        }

        "httpupgrade" -> {
            val opts = n.map("http-upgrade-opts")?.let { Node(it) }
            Transport.HttpUpgrade(path = opts?.str("path") ?: "/", host = opts?.str("host"))
        }

        else -> Transport.Tcp
    }

    private fun parseGroup(n: Node): ProxyGroup? {
        val name = n.str("name") ?: return null
        val type = when (n.str("type")?.lowercase()) {
            "select", "relay" -> ProxyGroup.GroupType.SELECT
            "url-test", "fallback", "load-balance" -> ProxyGroup.GroupType.URLTEST
            else -> return null
        }
        return ProxyGroup(
            name = name,
            type = type,
            members = n.strList("proxies"),
            testUrl = n.str("url"),
            intervalSeconds = n.int("interval"),
            tolerance = n.int("tolerance"),
        )
    }

    /** Map 字段安全读取包装。 */
    private class Node(private val map: Map<*, *>) {
        fun str(key: String): String? = map[key]?.let {
            if (it is String) it else it.toString()
        }?.takeIf { it.isNotEmpty() && it != "null" }

        fun int(key: String): Int? = when (val v = map[key]) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull()
            else -> null
        }

        fun bool(key: String): Boolean? = when (val v = map[key]) {
            is Boolean -> v
            is String -> v.toBooleanStrictOrNull()
            else -> null
        }

        fun map(key: String): Map<*, *>? = map[key] as? Map<*, *>

        fun strList(key: String): List<String> =
            (map[key] as? List<*>).orEmpty().mapNotNull { it?.toString() }
    }
}
