package com.pokernight.shared

interface Platform {
    val name: String
    val isDebug: Boolean
}

expect fun getPlatform(): Platform
