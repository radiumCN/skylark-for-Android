package com.radium.skylark.converter.model

/**
 * 协议无关的中间模型。
 *
 * 所有订阅格式（分享链接 / Clash YAML / sing-box JSON）先解析为 [ProxyNode]，
 * 再由 OutboundMapper 统一映射为 sing-box outbound，避免 N×M 转换。
 */
sealed interface ProxyNode {
    /** 节点显示名 / outbound tag */
    val tag: String
    val server: String
    val serverPort: Int

    data class Shadowsocks(
        override val tag: String,
        override val server: String,
        override val serverPort: Int,
        val method: String,
        val password: String,
        val plugin: String? = null,
        val pluginOpts: String? = null,
    ) : ProxyNode

    data class Vmess(
        override val tag: String,
        override val server: String,
        override val serverPort: Int,
        val uuid: String,
        val alterId: Int = 0,
        val security: String = "auto",
        val tls: TlsOptions? = null,
        val transport: Transport = Transport.Tcp,
    ) : ProxyNode

    data class Vless(
        override val tag: String,
        override val server: String,
        override val serverPort: Int,
        val uuid: String,
        val flow: String? = null,
        val tls: TlsOptions? = null,
        val transport: Transport = Transport.Tcp,
    ) : ProxyNode

    data class Trojan(
        override val tag: String,
        override val server: String,
        override val serverPort: Int,
        val password: String,
        val tls: TlsOptions? = TlsOptions(),
        val transport: Transport = Transport.Tcp,
    ) : ProxyNode

    data class Hysteria2(
        override val tag: String,
        override val server: String,
        override val serverPort: Int,
        val password: String,
        val obfsPassword: String? = null,
        val tls: TlsOptions? = TlsOptions(),
    ) : ProxyNode

    data class Tuic(
        override val tag: String,
        override val server: String,
        override val serverPort: Int,
        val uuid: String,
        val password: String,
        val congestionControl: String = "bbr",
        val tls: TlsOptions? = TlsOptions(),
    ) : ProxyNode
}

/** TLS / uTLS / REALITY 选项。 */
data class TlsOptions(
    val enabled: Boolean = true,
    val serverName: String? = null,
    val insecure: Boolean = false,
    val alpn: List<String> = emptyList(),
    /** uTLS 指纹，如 chrome / firefox */
    val fingerprint: String? = null,
    val realityPublicKey: String? = null,
    val realityShortId: String? = null,
)

/** 传输层。 */
sealed interface Transport {
    data object Tcp : Transport

    data class Ws(
        val path: String = "/",
        val host: String? = null,
        val headers: Map<String, String> = emptyMap(),
    ) : Transport

    data class Grpc(val serviceName: String) : Transport

    data class HttpUpgrade(
        val path: String = "/",
        val host: String? = null,
    ) : Transport
}
