package com.radium.skylark.converter

import com.radium.skylark.converter.link.ShareLinkParser
import com.radium.skylark.converter.model.ProxyNode
import com.radium.skylark.converter.model.Transport
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareLinkParserTest {

    private fun b64(s: String): String =
        Base64.getEncoder().encodeToString(s.toByteArray())

    @Test
    fun parseShadowsocks_sip002() {
        val userInfo = b64("aes-256-gcm:pass123")
        val link = "ss://$userInfo@example.com:8388#HK-Node"
        val node = ShareLinkParser.parse(link)
        assertTrue(node is ProxyNode.Shadowsocks)
        node as ProxyNode.Shadowsocks
        assertEquals("HK-Node", node.tag)
        assertEquals("example.com", node.server)
        assertEquals(8388, node.serverPort)
        assertEquals("aes-256-gcm", node.method)
        assertEquals("pass123", node.password)
    }

    @Test
    fun parseVmess_ws_tls() {
        val jsonStr = """
            {"v":"2","ps":"US-VMess","add":"cdn.example.com","port":"443","id":"11111111-2222-3333-4444-555555555555",
             "aid":"0","scy":"auto","net":"ws","host":"cdn.example.com","path":"/ws","tls":"tls","sni":"cdn.example.com"}
        """.trimIndent()
        val link = "vmess://" + b64(jsonStr)
        val node = ShareLinkParser.parse(link)
        assertTrue(node is ProxyNode.Vmess)
        node as ProxyNode.Vmess
        assertEquals("US-VMess", node.tag)
        assertEquals("cdn.example.com", node.server)
        assertEquals(443, node.serverPort)
        assertEquals("11111111-2222-3333-4444-555555555555", node.uuid)
        val transport = node.transport
        assertTrue(transport is Transport.Ws)
        assertEquals("/ws", (transport as Transport.Ws).path)
        assertEquals(true, node.tls?.enabled)
    }

    @Test
    fun parseVless_reality() {
        val link = "vless://uuid-abc@1.2.3.4:443?encryption=none&security=reality&sni=www.apple.com" +
            "&fp=chrome&pbk=PUBKEY&sid=abcd&type=tcp&flow=xtls-rprx-vision#Reality"
        val node = ShareLinkParser.parse(link)
        assertTrue(node is ProxyNode.Vless)
        node as ProxyNode.Vless
        assertEquals("Reality", node.tag)
        assertEquals("1.2.3.4", node.server)
        assertEquals(443, node.serverPort)
        assertEquals("xtls-rprx-vision", node.flow)
        assertEquals("www.apple.com", node.tls?.serverName)
        assertEquals("chrome", node.tls?.fingerprint)
        assertEquals("PUBKEY", node.tls?.realityPublicKey)
    }

    @Test
    fun parseTrojan() {
        val link = "trojan://secret@trojan.example.com:443?sni=trojan.example.com#Trojan"
        val node = ShareLinkParser.parse(link)
        assertTrue(node is ProxyNode.Trojan)
        node as ProxyNode.Trojan
        assertEquals("secret", node.password)
        assertEquals("trojan.example.com", node.server)
        assertEquals(443, node.serverPort)
    }

    @Test
    fun parseHysteria2() {
        val link = "hysteria2://pw@hy2.example.com:8443?sni=hy2.example.com&insecure=1#HY2"
        val node = ShareLinkParser.parse(link)
        assertTrue(node is ProxyNode.Hysteria2)
        node as ProxyNode.Hysteria2
        assertEquals("pw", node.password)
        assertEquals(8443, node.serverPort)
        assertEquals(true, node.tls?.insecure)
    }

    @Test
    fun parseTuic() {
        val link = "tuic://uuid-x:passy@tuic.example.com:443?congestion_control=bbr&sni=tuic.example.com#TUIC"
        val node = ShareLinkParser.parse(link)
        assertTrue(node is ProxyNode.Tuic)
        node as ProxyNode.Tuic
        assertEquals("uuid-x", node.uuid)
        assertEquals("passy", node.password)
        assertEquals("bbr", node.congestionControl)
    }

    @Test
    fun parseMany_skipsInvalid() {
        val ss = "ss://" + b64("aes-256-gcm:p") + "@a.com:1080#A"
        val text = "$ss\ninvalid-line\n\nhttp://not-a-node"
        val nodes = ShareLinkParser.parseMany(text)
        assertEquals(1, nodes.size)
    }

    @Test
    fun parseUnknownScheme_returnsNull() {
        assertNull(ShareLinkParser.parse("ftp://whatever"))
    }
}
