package com.pokernight.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnvironmentTest {
    @Test
    fun testPlatformSanity() {
        val platform = getPlatform()
        assertTrue(platform.name.isNotBlank(), "Platform name should not be blank")
    }

    @Test
    fun testBuildInfo() {
        val info = Environment.getBuildInfo()
        assertTrue(info.startsWith("Poker Night v1.0"))
    }

    @Test
    fun testDefaultChipValueIsOneCentOrPaise() {
        assertEquals(1, Environment.DEFAULT_CHIP_VALUE_IN_PAISE)
    }

    @Test
    fun testChipValidation() {
        assertTrue(Environment.isZeroOrPositiveChips(0L))
        assertTrue(Environment.isZeroOrPositiveChips(1000L))
        assertFalse(Environment.isZeroOrPositiveChips(-1L))
    }
}
