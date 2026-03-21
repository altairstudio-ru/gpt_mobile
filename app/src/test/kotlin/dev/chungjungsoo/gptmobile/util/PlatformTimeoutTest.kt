package dev.chungjungsoo.gptmobile.util

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Test

class PlatformTimeoutTest {

    @Test
    fun `platform timeout maps to socket timeout millis`() {
        assertEquals(30_000L, platformTimeoutSecondsToSocketTimeoutMillis(30))
    }

    @Test
    fun `zero platform timeout disables socket timeout`() {
        assertNull(platformTimeoutSecondsToSocketTimeoutMillis(0))
    }

    @Test
    fun `formatPlatformTimeout shows off when disabled`() {
        assertEquals("Off", formatPlatformTimeout(0, "Off"))
    }
}
