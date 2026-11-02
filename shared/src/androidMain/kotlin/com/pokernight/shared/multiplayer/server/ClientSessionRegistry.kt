package com.pokernight.shared.multiplayer.server

import com.pokernight.shared.model.PlayerId
import com.pokernight.shared.multiplayer.protocol.ServerMessage
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.send
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

data class ConnectedClient(
    val connectionId: String,
    val session: DefaultWebSocketServerSession,
    var seatToken: String? = null,
    var playerId: PlayerId? = null,
    var lastActiveAt: Long = System.currentTimeMillis(),
)

class ClientSessionRegistry(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val lock = Mutex()
    private val connections = ConcurrentHashMap<String, ConnectedClient>()
    private val seatTokenToPlayerMap = ConcurrentHashMap<String, PlayerId>()

    suspend fun registerConnection(connectionId: String, session: DefaultWebSocketServerSession) {
        lock.withLock {
            connections[connectionId] = ConnectedClient(
                connectionId = connectionId,
                session = session,
            )
        }
    }

    suspend fun unregisterConnection(connectionId: String) {
        lock.withLock {
            connections.remove(connectionId)
        }
    }

    suspend fun bindSeat(connectionId: String, seatToken: String, playerId: PlayerId) {
        lock.withLock {
            seatTokenToPlayerMap[seatToken] = playerId
            connections[connectionId]?.let { client ->
                client.seatToken = seatToken
                client.playerId = playerId
                client.lastActiveAt = System.currentTimeMillis()
            }
        }
    }

    fun getPlayerIdForSeatToken(seatToken: String): PlayerId? = seatTokenToPlayerMap[seatToken]

    fun getClient(connectionId: String): ConnectedClient? = connections[connectionId]

    fun getAllClients(): List<ConnectedClient> = connections.values.toList()

    suspend fun sendMessage(session: DefaultWebSocketServerSession, message: ServerMessage) {
        val payload = json.encodeToString(message)
        session.send(payload)
    }

    suspend fun broadcastToAll(message: ServerMessage) {
        val payload = json.encodeToString(message)
        connections.values.forEach { client ->
            try {
                client.session.send(payload)
            } catch (_: Exception) {
                // Connection closed
            }
        }
    }

    suspend fun sendToPlayer(playerId: PlayerId, message: ServerMessage) {
        val payload = json.encodeToString(message)
        connections.values.filter { it.playerId == playerId }.forEach { client ->
            try {
                client.session.send(payload)
            } catch (_: Exception) {
                // Connection closed
            }
        }
    }
}
