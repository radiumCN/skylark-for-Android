package com.radium.skylark.converter.link

import com.radium.skylark.converter.model.ProxyNode
import com.radium.skylark.converter.model.TlsOptions
import com.radium.skylark.converter.model.Transport
import java.net.URLDecoder
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 将分享链接（vmess:// vless:// ss:// trojan:// hysteria2:// tuic://）解析为 [ProxyNode]。
 *
 * 解析失败的单条链接返回 null（不抛出），批量解析时跳过无效项。
 */
object ShareLinkParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 解析多行文本（每行一个链接），返回成功解析的节点。 */
    fun parseMany(text: String): List<ProxyNode> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { runCatching { parse(it) }.getOrNull() }
            .toList()

    fun parse(link: String): ProxyNode? {
        val trimmed = link.trim()
        return when {
            trimmed.startsWith("ss://") -> parseShadowsocks(trimmed)
            trimmed.startsWith("vmess://") -> parseVmess(trimmed)
            trimmed.startsWith("vless://") -> parseVless(trimmed)
            trimmed.startsWith("trojan://") -> parseTrojan(trimmed)
            trimmed.startsWith("hysteria2://") || trimmed.startsWith("hy2://") -> parseHysteria2(trimmed)
            trimmed.startsWith("tuic://") -> parseTuic(trimmed)
            else -> null
        }
    }

    // ---- Shadowsocks ----
    private fun parseShadowsocks(link: String): ProxyNode? {
        val (body, fragment) = splitFragment(link.removePrefix("ss://"))
        val name = fragment ?: "ss"
        val atIndex = body.lastIndexOf('@')
        return if (atIndex >= 0) {
            // SIP002: ss://base64(method:password)@host:port/?plugin=...
            val userInfo = body.substring(0, atIndex)
            val hostPart = body.substring(atIndex + 1).substringBefore("/")
            val decodedUser = decodeBase64OrNull(userInfo) ?: urlDecode(userInfo)
            val (method, password) = decodedUser.split(":", limit = 2).let {
                (it.getOrNull(0) ?: return null) to (it.getOrNull(1) ?: return null)
            }
            val (host, port) = splitHostPort(hostPart) ?: return null
            val query = parseQuery(body.substringAfter('?', ""))
            ProxyNode.Shadowsocks(name, host, port, method, password, plugin = query["plugin"])
        } else {
            // Legacy: ss://base64(method:password@host:port)
            val decoded = decodeBase64OrNull(body) ?: return null
            val at = decoded.lastIndexOf('@')
            if (at < 0) return null
            val (method, password) = decoded.substring(0, at).split(":", limit = 2).let {
                (it.getOrNull(0) ?: return null) to (it.getOrNull(1) ?: return null)
            }
            val (host, port) = splitHostPort(decoded.substring(at + 1)) ?: return null
            ProxyNode.Shadowsocks(name, host, port, method, password)
        }
    }

    // ---- VMess (v2rayN base64 JSON) ----
    private fun parseVmess(link: String): ProxyNode? {
        val decoded = decodeBase64OrNull(link.removePrefix("vmess://")) ?: return null
        val obj = runCatching { json.parseToJsonElement(decoded) as? JsonObject }.getOrNull() ?: return null
        fun str(key: String) = obj[key]?.jsonPrimitive?.contentOrNull
        fun int(key: String) = obj[key]?.jsonPrimitive?.intOrNull
            ?: str(key)?.toIntOrNull()

        val server = str("add") ?: return null
        val port = int("port") ?: return null
        val uuid = str("id") ?: return null
        val name = str("ps") ?: "vmess"
        val net = str("net") ?: "tcp"
        val host = str("host")
        val path = str("path") ?: "/"
        val tlsEnabled = str("tls").equals("tls", ignoreCase = true)
        val transport = when (net) {
            "ws" -> Transport.Ws(path = path, host = host)
            "grpc" -> Transport.Grpc(serviceName = path.trim('/'))
            "httpupgrade" -> Transport.HttpUpgrade(path = path, host = host)
            else -> Transport.Tcp
        }
        val tls = if (tlsEnabled) TlsOptions(serverName = str("sni") ?: host) else null
        return ProxyNode.Vmess(
            tag = name, server = server, serverPort = port, uuid = uuid,
            alterId = int("aid") ?: 0, security = str("scy") ?: "auto",
            tls = tls, transport = transport,
        )
    }

    // ---- VLESS ----
    private fun parseVless(link: String): ProxyNode? {
        val u = UriParts.of(link.removePrefix("vless://")) ?: return null
        val uuid = u.userInfo ?: return null
        val q = u.query
        val security = q["security"] ?: "none"
        val tls = if (security == "tls" || security == "reality") {
            TlsOptions(
                serverName = q["sni"] ?: q["host"],
                fingerprint = q["fp"],
                realityPublicKey = q["pbk"],
                realityShortId = q["sid"],
                alpn = q["alpn"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            )
        } else null
        return ProxyNode.Vless(
            tag = u.name ?: "vless", server = u.host, serverPort = u.port, uuid = uuid,
            flow = q["flow"], tls = tls, transport = parseTransport(q),
        )
    }

    // ---- Trojan ----
    private fun parseTrojan(link: String): ProxyNode? {
        val u = UriParts.of(link.removePrefix("trojan://")) ?: return null
        val password = u.userInfo ?: return null
        val q = u.query
        val tls = TlsOptions(
            serverName = q["sni"] ?: q["peer"] ?: q["host"],
            insecure = q["allowInsecure"] == "1",
            alpn = q["alpn"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
        )
        return ProxyNode.Trojan(
            tag = u.name ?: "trojan", server = u.host, serverPort = u.port,
            password = password, tls = tls, transport = parseTransport(q),
        )
    }

    // ---- Hysteria2 ----
    private fun parseHysteria2(link: String): ProxyNode? {
        val body = link.removePrefix("hysteria2://").removePrefix("hy2://")
        val u = UriParts.of(body) ?: return null
        val password = u.userInfo ?: return null
        val q = u.query
        return ProxyNode.Hysteria2(
            tag = u.name ?: "hysteria2", server = u.host, serverPort = u.port,
            password = password, obfsPassword = q["obfs-password"],
            tls = TlsOptions(
                serverName = q["sni"],
                insecure = q["insecure"] == "1",
            ),
        )
    }

    // ---- TUIC ----
    private fun parseTuic(link: String): ProxyNode? {
        val u = UriParts.of(link.removePrefix("tuic://")) ?: return null
        val userInfo = u.userInfo ?: return null
        val (uuid, password) = userInfo.split(":", limit = 2).let {
            (it.getOrNull(0) ?: return null) to (it.getOrNull(1) ?: "")
        }
        val q = u.query
        return ProxyNode.Tuic(
            tag = u.name ?: "tuic", server = u.host, serverPort = u.port,
            uuid = uuid, password = password,
            congestionControl = q["congestion_control"] ?: "bbr",
            tls = TlsOptions(
                serverName = q["sni"],
                insecure = q["allow_insecure"] == "1",
                alpn = q["alpn"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            ),
        )
    }

    private fun parseTransport(q: Map<String, String>): Transport = when (q["type"]) {
        "ws" -> Transport.Ws(path = q["path"] ?: "/", host = q["host"])
        "grpc" -> Transport.Grpc(serviceName = q["serviceName"] ?: "")
        "httpupgrade" -> Transport.HttpUpgrade(path = q["path"] ?: "/", host = q["host"])
        else -> Transport.Tcp
    }

    // ---- helpers ----
    private fun splitFragment(s: String): Pair<String, String?> {
        val idx = s.indexOf('#')
        return if (idx < 0) s to null else s.substring(0, idx) to urlDecode(s.substring(idx + 1))
    }

    private fun splitHostPort(s: String): Pair<String, Int>? {
        val clean = s.substringBefore('/').substringBefore('?')
        val idx = clean.lastIndexOf(':')
        if (idx < 0) return null
        val host = clean.substring(0, idx).trim('[', ']')
        val port = clean.substring(idx + 1).toIntOrNull() ?: return null
        return host to port
    }

    private fun parseQuery(query: String): Map<String, String> =
        query.split('&')
            .filter { it.contains('=') }
            .associate {
                val (k, v) = it.split('=', limit = 2)
                urlDecode(k) to urlDecode(v)
            }

    private fun decodeBase64OrNull(input: String): String? {
        val s = input.trim().replace('-', '+').replace('_', '/')
        val padded = when (s.length % 4) {
            2 -> "$s=="
            3 -> "$s="
            else -> s
        }
        return runCatching { String(Base64.getDecoder().decode(padded)) }.getOrNull()
            ?: runCatching { String(Base64.getMimeDecoder().decode(padded)) }.getOrNull()
    }

    private fun urlDecode(s: String): String =
        runCatching { URLDecoder.decode(s, Charsets.UTF_8.name()) }.getOrDefault(s)

    /** userinfo@host:port?query#name 的通用拆解。 */
    private data class UriParts(
        val userInfo: String?,
        val host: String,
        val port: Int,
        val query: Map<String, String>,
        val name: String?,
    ) {
        companion object {
            fun of(raw: String): UriParts? {
                val (beforeFrag, name) = raw.indexOf('#').let {
                    if (it < 0) raw to null
                    else raw.substring(0, it) to runCatching {
                        URLDecoder.decode(raw.substring(it + 1), Charsets.UTF_8.name())
                    }.getOrDefault(raw.substring(it + 1))
                }
                val query = beforeFrag.substringAfter('?', "")
                val authority = beforeFrag.substringBefore('?')
                val at = authority.lastIndexOf('@')
                val userInfo = if (at >= 0) authority.substring(0, at) else null
                val hostPort = if (at >= 0) authority.substring(at + 1) else authority
                val portIdx = hostPort.lastIndexOf(':')
                if (portIdx < 0) return null
                val host = hostPort.substring(0, portIdx).trim('[', ']')
                val port = hostPort.substring(portIdx + 1).substringBefore('/').toIntOrNull() ?: return null
                val q = query.split('&')
                    .filter { it.contains('=') }
                    .associate {
                        val (k, v) = it.split('=', limit = 2)
                        val dk = runCatching { URLDecoder.decode(k, Charsets.UTF_8.name()) }.getOrDefault(k)
                        val dv = runCatching { URLDecoder.decode(v, Charsets.UTF_8.name()) }.getOrDefault(v)
                        dk to dv
                    }
                return UriParts(userInfo, host, port, q, name)
            }
        }
    }
}
