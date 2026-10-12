package com.pokernight.shared

object Environment {
    const val APP_NAME = "Poker Night"
    const val SPEC_VERSION = "1.0"
    const val DEFAULT_CHIP_VALUE_IN_PAISE = 1 // 1 chip = ₹0.01

    fun getBuildInfo(): String {
        val platform = getPlatform()
        return "$APP_NAME v$SPEC_VERSION running on ${platform.name}"
    }

    fun isZeroOrPositiveChips(amount: Long): Boolean = amount >= 0L
}
