package com.radium.skylark.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {

    @Test
    fun parses_stable_and_prerelease() {
        val stable = SemVer.parseOrNull("0.1.0")!!
        assertEquals(0, stable.major)
        assertEquals(1, stable.minor)
        assertEquals(0, stable.patch)
        assertTrue(!stable.isPreRelease)

        val beta = SemVer.parseOrNull("0.1.0-beta.1")!!
        assertTrue(beta.isPreRelease)
        assertEquals("0.1.0-beta.1", beta.toString())
    }

    @Test
    fun accepts_v_prefix() {
        assertEquals(SemVer.parseOrNull("1.2.3"), SemVer.parseOrNull("v1.2.3"))
    }

    @Test
    fun rejects_invalid() {
        assertNull(SemVer.parseOrNull("not-a-version"))
        assertNull(SemVer.parseOrNull("1.2"))
        assertNull(SemVer.parseOrNull(null))
    }

    @Test
    fun stable_is_greater_than_its_prerelease() {
        val stable = SemVer.parseOrNull("0.1.0")!!
        val beta = SemVer.parseOrNull("0.1.0-beta.1")!!
        assertTrue(stable > beta)
    }

    @Test
    fun higher_beta_number_is_greater() {
        val b1 = SemVer.parseOrNull("0.1.0-beta.1")!!
        val b2 = SemVer.parseOrNull("0.1.0-beta.2")!!
        assertTrue(b2 > b1)
    }

    @Test
    fun core_version_ordering() {
        assertTrue(SemVer.parseOrNull("0.2.0")!! > SemVer.parseOrNull("0.1.9")!!)
        assertTrue(SemVer.parseOrNull("1.0.0")!! > SemVer.parseOrNull("0.9.9")!!)
        assertTrue(SemVer.parseOrNull("0.1.1")!! > SemVer.parseOrNull("0.1.0")!!)
    }

    @Test
    fun channel_default_from_current_version() {
        assertEquals(UpdateChannel.BETA, UpdateChannel.default("0.1.0-beta.1"))
        assertEquals(UpdateChannel.STABLE, UpdateChannel.default("0.1.0"))
    }
}
