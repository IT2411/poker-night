package com.pokernight.android.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL

object NetworkUtils {

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()

            val priorityInterfaces = interfaces.filter { iface ->
                !iface.isLoopback &&
                    iface.isUp &&
                    (iface.name.contains("wlan", ignoreCase = true) || iface.name.contains("ap", ignoreCase = true))
            }

            for (iface in priorityInterfaces) {
                for (addr in iface.inetAddresses.toList()) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: continue
                    }
                }
            }

            for (iface in interfaces.filter { !it.isLoopback && it.isUp }) {
                for (addr in iface.inetAddresses.toList()) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: continue
                    }
                }
            }
        } catch (_: Exception) {
        }

        return "127.0.0.1"
    }

    /**
     * Scans the local Wi-Fi / Hotspot subnet to locate the Host device advertising the room code.
     */
    suspend fun findHostByRoomCode(roomCode: String, port: Int = 8080): String? = withContext(Dispatchers.IO) {
        val cleanCode = roomCode.trim().removePrefix("ROOM-").uppercase()
        val myIp = getLocalIpAddress()

        val candidates = mutableListOf<String>()
        // 1. Always check default Android hotspot gateway
        candidates.add("192.168.43.1")

        // 2. Add local subnet IPs
        if (myIp.contains(".")) {
            val subnetPrefix = myIp.substringBeforeLast(".")
            for (i in 1..254) {
                val ip = "$subnetPrefix.$i"
                if (ip != myIp) {
                    candidates.add(ip)
                }
            }
        }

        coroutineScope {
            val deferreds = candidates.map { ip ->
                async {
                    checkHostAtIp(ip, port, cleanCode)
                }
            }
            deferreds.awaitAll().firstOrNull { it != null }
        }
    }

    private fun checkHostAtIp(ip: String, port: Int, targetCode: String): String? = try {
        val url = URL("http://$ip:$port/health")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 350
        conn.readTimeout = 350
        conn.requestMethod = "GET"
        if (conn.responseCode == 200) {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            if (body.contains(targetCode, ignoreCase = true)) {
                "http://$ip:$port"
            } else {
                null
            }
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}
