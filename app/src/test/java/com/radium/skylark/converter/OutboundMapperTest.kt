package com.radium.skylark.converter

import com.radium.skylark.converter.mapper.OutboundMapper
import com.radium.skylark.converter.model.ProxyNode
import com.radium.skylark.converter.model.TlsOptions
import com.radium.skylark.converter.model.Transport
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboundMapperTest {

    @Test
    fun mapVmess_wsTls() {
        val node = ProxyNode.Vmess(
            tag = "n1", server = "a.com", serverPort = 443, uuid = "uid",
            tls = TlsOptions(serverName = "a.com"),
            transport = Transport.Ws(path = "/ws", host = "a.com"),
        )
        val out = OutboundMapper.toOutbound(node)
        assertEquals("vmess", out["type"]?.jsonPrimitive?.content)
        assertEquals("n1", out["tag"]?.jsonPrimitive?.content)
        assertEquals("uid", out["uuid"]?.jsonPrimitive?.content)
        val tls = out["tls"]!!.jsonObject
        assertEquals("a.com", tls["server_name"]?.jsonPrimitive?.content)
        val transport = out["transport"]!!.jsonObject
        assertEquals("ws", transport["type"]?.jsonPrimitive?.content)
        assertEquals("/ws", transport["path"]?.jsonPrimitive?.content)
        assertEquals("a.com", transport["headers"]!!.jsonObject["Host"]?.jsonPrimitive?.content)
    }

    @Test
    fun mapShadowsocks() {
        val node = ProxyNode.Shadowsocks("ss1", "b.com", 8388, "aes-256-gcm", "pw")
        val out: JsonObject = OutboundMapper.toOutbound(node)
        assertEquals("shadowsocks", out["type"]?.jsonPrimitive?.content)
        assertEquals("aes-256-gcm", out["method"]?.jsonPrimitive?.content)
        assertEquals(8388, out["server_port"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun mapVless_reality() {
        val node = ProxyNode.Vless(
            tag = "v", server = "1.1.1.1", serverPort = 443, uuid = "u",
            flow = "xtls-rprx-vision",
            tls = TlsOptions(serverName = "apple.com", fingerprint = "chrome", realityPublicKey = "PK", realityShortId = "sid"),
        )
        val out = OutboundMapper.toOutbound(node)
        val tls = out["tls"]!!.jsonObject
        assertTrue(tls.containsKey("utls"))
        assertEquals("chrome", tls["utls"]!!.jsonObject["fingerprint"]?.jsonPrimitive?.content)
        assertEquals("PK", tls["reality"]!!.jsonObject["public_key"]?.jsonPrimitive?.content)
        assertEquals("xtls-rprx-vision", out["flow"]?.jsonPrimitive?.content)
    }
}
