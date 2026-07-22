package com.radium.skylark.converter

import com.radium.skylark.converter.model.ProxyNode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigBuilderTest {

    private val sampleNodes = listOf(
        ProxyNode.Shadowsocks("SS-A", "a.com", 8388, "aes-256-gcm", "pw"),
        ProxyNode.Trojan("TJ-B", "b.com", 443, "secret"),
    )

    private fun outbounds(config: kotlinx.serialization.json.JsonObject): JsonArray =
        config["outbounds"]!!.jsonArray

    @Test
    fun buildsAutoAndSelectorGroups() {
        val config = ConfigBuilder.build(sampleNodes)
        val outs = outbounds(config)
        val tags = outs.map { it.jsonObject["tag"]?.jsonPrimitive?.content }
        assertTrue(tags.contains("proxy"))
        assertTrue(tags.contains("Auto"))
        assertTrue(tags.contains("SS-A"))
        assertTrue(tags.contains("TJ-B"))
        assertTrue(tags.contains("direct"))

        val auto = outs.first { it.jsonObject["tag"]?.jsonPrimitive?.content == "Auto" }.jsonObject
        assertEquals("urltest", auto["type"]?.jsonPrimitive?.content)
        val autoMembers = auto["outbounds"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("SS-A", "TJ-B"), autoMembers)

        val selector = outs.first { it.jsonObject["tag"]?.jsonPrimitive?.content == "proxy" }.jsonObject
        assertEquals("Auto", selector["default"]?.jsonPrimitive?.content)
    }

    @Test
    fun emptyNodes_selectorDefaultsToDirect() {
        val config = ConfigBuilder.build(emptyList())
        val outs = outbounds(config)
        val tags = outs.map { it.jsonObject["tag"]?.jsonPrimitive?.content }
        assertTrue(tags.contains("proxy"))
        assertTrue(!tags.contains("Auto"))
        val selector = outs.first { it.jsonObject["tag"]?.jsonPrimitive?.content == "proxy" }.jsonObject
        assertEquals("direct", selector["default"]?.jsonPrimitive?.content)
    }

    @Test
    fun containsInboundsAndRoute() {
        val config = ConfigBuilder.build(sampleNodes)
        assertNotNull(config["inbounds"])
        assertNotNull(config["route"])
        assertNotNull(config["dns"])
        val route = config["route"]!!.jsonObject
        assertEquals("proxy", route["final"]?.jsonPrimitive?.content)
    }

    @Test
    fun jsonStringIsValid() {
        val jsonStr = ConfigBuilder.buildJsonString(sampleNodes)
        assertTrue(jsonStr.contains("\"outbounds\""))
        assertTrue(jsonStr.contains("\"urltest\""))
    }
}
