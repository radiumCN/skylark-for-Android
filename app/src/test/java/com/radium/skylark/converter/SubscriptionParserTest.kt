package com.radium.skylark.converter

import com.radium.skylark.converter.model.ProxyNode
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionParserTest {

    private fun b64(s: String): String = Base64.getEncoder().encodeToString(s.toByteArray())

    @Test
    fun detectsShareLinks() {
        val text = "ss://" + b64("aes-256-gcm:pw") + "@a.com:8388#A"
        assertEquals(SubscriptionParser.Format.SHARE_LINKS, SubscriptionParser.detect(text))
        val result = SubscriptionParser.parse(text)
        assertEquals(1, result.nodes.size)
        assertTrue(result.nodes[0] is ProxyNode.Shadowsocks)
    }

    @Test
    fun detectsClashYaml() {
        val yaml = "proxies:\n  - name: A\n    type: ss\n    server: a.com\n    port: 8388\n    cipher: aes-256-gcm\n    password: pw\n"
        assertEquals(SubscriptionParser.Format.CLASH_YAML, SubscriptionParser.detect(yaml))
        assertEquals(1, SubscriptionParser.parse(yaml).nodes.size)
    }

    @Test
    fun detectsAndDecodesBase64AggregatedSub() {
        val links = buildString {
            appendLine("ss://" + b64("aes-256-gcm:pw") + "@a.com:8388#A")
            appendLine("trojan://secret@b.com:443?sni=b.com#B")
        }
        val encoded = b64(links)
        assertEquals(SubscriptionParser.Format.BASE64, SubscriptionParser.detect(encoded))
        val result = SubscriptionParser.parse(encoded)
        assertEquals(2, result.nodes.size)
    }

    @Test
    fun unknownReturnsEmpty() {
        val result = SubscriptionParser.parse("just some random text 123")
        assertTrue(result.nodes.isEmpty())
    }
}
