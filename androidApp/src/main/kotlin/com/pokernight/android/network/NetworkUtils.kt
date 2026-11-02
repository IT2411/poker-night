package com.pokernight.android.network

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    /**
     * Resolves the device's local IPv4 address, prioritizing Wi-Fi (wlan)
     * and Hotspot (ap/swlan) network interfaces.
     */
    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()

            // 1. Prioritize Wi-Fi and Hotspot interfaces
            val priorityInterfaces = interfaces.filter { iface ->
                !iface.isLoopback && iface.isUp &&
                    (iface.name.contains("wlan", ignoreCase = true) || iface.name.contains("ap", ignoreCase = true))
            }

            for (iface in priorityInterfaces) {
                for (addr in iface.inetAddresses.toList()) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: continue
                    }
                }
            }

            // 2. Fallback to any active non-loopback IPv4 interface
            for (iface in interfaces.filter { !it.isLoopback && it.isUp }) {
                for (addr in iface.inetAddresses.toList()) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: continue
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore socket/interface inspection errors
        }

        return "127.0.0.1"
    }
}
