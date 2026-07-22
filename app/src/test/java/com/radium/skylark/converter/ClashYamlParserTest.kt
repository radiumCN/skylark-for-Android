package com.radium.skylark.converter

import com.radium.skylark.converter.clash.ClashYamlParser
import com.radium.skylark.converter.model.ProxyGroup
import com.radium.skylark.converter.model.ProxyNode
import com.radium.skylark.converter.model.Transport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClashYamlParserTest {

    private val yaml = """
        mixed-port: 7890
        proxies:
          - name: "SS-HK"
            type: ss
            server: hk.example.com
            port: 8388
            cipher: aes-256-gcm
            password: pass123
          - name: "VMess-US"
            type: vmess
            server: us.example.com
            port: 443
            uuid: 11111111-2222-3333-4444-555555555555
            alterId: 0
            cipher: auto
            network: ws
            tls: true
            servername: us.example.com
            ws-opts:
              path: /ray
              headers:
                Host: us.example.com
          - name: "Trojan-JP"
            type: trojan
            server: jp.example.com
            port: 443
            password: trojanpw
            sni: jp.example.com
          - name: "Unsupported"
            type: snell
            server: x.example.com
            port: 1234
        proxy-groups:
          - name: "PROXY"
            type: select
            proxies:
              - "AUTO"
              - "SS-HK"
              - "VMess-US"
              - DIRECT
          - name: "AUTO"
            type: url-test
            proxies:
              - "SS-HK"
              - "VMess-US"
              - "Trojan-JP"
            url: http://www.gstatic.com/generate_204
            interval: 300
    """.trimIndent()

    @Test
    fun detectsClashYaml() {
        assertTrue(ClashYamlParser.isClashYaml(yaml))
    }

    @Test
    fun parsesProxies_skippingUnsupported() {
        val result = ClashYamlParser.parse(yaml)
        // snell 不支持，应被跳过 -> 3 个
        assertEquals(3, result.nodes.size)
        val ss = result.nodes[0] as ProxyNode.Shadowsocks
        assertEquals("SS-HK", ss.tag)
        assertEquals("aes-256-gcm", ss.method)

        val vmess = result.nodes[1] as ProxyNode.Vmess
        assertEquals("us.example.com", vmess.server)
        assertEquals(true, vmess.tls?.enabled)
        val transport = vmess.transport
        assertTrue(transport is Transport.Ws)
        assertEquals("/ray", (transport as Transport.Ws).path)
        assertEquals("us.example.com", transport.host)
    }

    @Test
    fun parsesProxyGroups() {
        val result = ClashYamlParser.parse(yaml)
        assertEquals(2, result.groups.size)
        val select = result.groups.first { it.name == "PROXY" }
        assertEquals(ProxyGroup.GroupType.SELECT, select.type)
        assertTrue(select.members.contains("AUTO"))

        val auto = result.groups.first { it.name == "AUTO" }
        assertEquals(ProxyGroup.GroupType.URLTEST, auto.type)
        assertEquals(300, auto.intervalSeconds)
    }

    @Test
    fun buildsConfigFromClashGroups() {
        val result = ClashYamlParser.parse(yaml)
        val config = ConfigBuilder.build(result)
        val outbounds = config["outbounds"]!!.let { it as kotlinx.serialization.json.JsonArray }
        val tags = outbounds.map { (it as kotlinx.serialization.json.JsonObject)["tag"] }
            .map { (it as kotlinx.serialization.json.JsonPrimitive).content }
        assertTrue(tags.contains("PROXY"))
        assertTrue(tags.contains("AUTO"))
        assertTrue(tags.contains("SS-HK"))
        assertTrue(tags.contains("direct"))
    }
}
