package com.pokernight.shared.multiplayer.client

import com.pokernight.shared.model.ActionType
import com.pokernight.shared.multiplayer.protocol.AvailableSeatDto
import com.pokernight.shared.multiplayer.protocol.ClientMessage
import com.pokernight.shared.multiplayer.protocol.ClientPayload
import com.pokernight.shared.multiplayer.protocol.SanitizedGameStateSnapshot
import com.pokernight.shared.multiplayer.protocol.ServerMessage
import com.pokernight.shared.multiplayer.protocol.ServerPayload
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

sealed interface ClientSessionState {
    data object Disconnected : ClientSessionState
    data object Connecting : ClientSessionState
    data class SelectingSeat(
        val availableSeats: List<AvailableSeatDto>,
        val roomId: String,
        val sessionName: String,
    ) : ClientSessionState
    data class InGame(
        val snapshot: SanitizedGameStateSnapshot,
        val myPlayerId: String,
        val seatToken: String,
    ) : ClientSessionState
    data class Error(val message: String) : ClientSessionState
}

class PokerMultiplayerClient {

    private val json = Json { ignoreUnknownKeys = true }
    private val clientScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var activeJob: Job? = null
    private var webSocketSession: DefaultClientWebSocketSession? = null

    private val _sessionState = MutableStateFlow<ClientSessionState>(ClientSessionState.Disconnected)
    val sessionState: StateFlow<ClientSessionState> = _sessionState.asStateFlow()

    private var targetHost: String = "127.0.0.1"
    private var targetPort: Int = 8080
    private var targetPairingSecret: String = ""
    private var cachedSeatToken: String? = null
    private var cachedPlayerId: String? = null
    private var currentRevision: Long = 1L

    fun connect(
        joinUrlOrHost: String,
        pairingTokenFallback: String = "",
        savedSeatToken: String? = null,
        savedPlayerId: String? = null,
    ) {
        disconnect()

        this.cachedSeatToken = savedSeatToken
        this.cachedPlayerId = savedPlayerId

        parseConnectionUrl(joinUrlOrHost, pairingTokenFallback)
        _sessionState.value = ClientSessionState.Connecting

        activeJob = clientScope.launch {
            val httpClient = HttpClient(CIO) {
                install(WebSockets)
            }

            try {
                // If not seated, fetch available seats from /lobby
                if (cachedSeatToken == null || cachedPlayerId == null) {
                    try {
                        val lobbyResp = httpClient.get("http://$targetHost:$targetPort/lobby").bodyAsText()
                        val serverPayload = json.decodeFromString<ServerPayload>(lobbyResp)
                        if (serverPayload is ServerPayload.RoomLobby) {
                            _sessionState.value = ClientSessionState.SelectingSeat(
                                availableSeats = serverPayload.availableSeats,
                                roomId = serverPayload.roomId,
                                sessionName = serverPayload.sessionName,
                            )
                        }
                    } catch (_: Exception) {
                        // Continue to WebSocket
                    }
                }

                httpClient.webSocket(host = targetHost, port = targetPort, path = "/ws/poker") {
                    webSocketSession = this

                    val token = cachedSeatToken
                    if (token != null) {
                        val reconnectMsg = ClientMessage(
                            actionId = "reconnect-${UUID.randomUUID()}",
                            seatToken = token,
                            payload = ClientPayload.Reconnect(lastSeenRevision = currentRevision),
                        )
                        send(json.encodeToString(reconnectMsg))
                    }

                    for (frame in incoming) {
                        if (!isActive) break
                        if (frame is Frame.Text) {
                            handleServerMessage(frame.readText())
                        }
                    }
                }
            } catch (e: Exception) {
                _sessionState.value = ClientSessionState.Error("Connection failed: ${e.message}")
            } finally {
                httpClient.close()
                if (_sessionState.value !is ClientSessionState.Error) {
                    _sessionState.value = ClientSessionState.Disconnected
                }
            }
        }
    }

    private fun parseConnectionUrl(input: String, pairingFallback: String) {
        var clean = input.trim()
        if (clean.contains("pair=")) {
            targetPairingSecret = clean.substringAfter("pair=").substringBefore("&")
        } else if (pairingFallback.isNotBlank()) {
            targetPairingSecret = pairingFallback
        }

        if (clean.startsWith("http://")) clean = clean.removePrefix("http://")
        if (clean.startsWith("https://")) clean = clean.removePrefix("https://")
        val hostPortPart = clean.substringBefore("/").substringBefore("?")

        if (hostPortPart.contains(":")) {
            targetHost = hostPortPart.substringBefore(":")
            targetPort = hostPortPart.substringAfter(":").toIntOrNull() ?: 8080
        } else {
            targetHost = if (hostPortPart.isNotBlank()) hostPortPart else "127.0.0.1"
            targetPort = 8080
        }
    }

    private fun handleServerMessage(rawText: String) {
        val serverMsg = try {
            json.decodeFromString<ServerMessage>(rawText)
        } catch (_: Exception) {
            return
        }

        currentRevision = serverMsg.stateRevision

        when (val payload = serverMsg.payload) {
            is ServerPayload.RoomLobby -> {
                _sessionState.value = ClientSessionState.SelectingSeat(
                    availableSeats = payload.availableSeats,
                    roomId = payload.roomId,
                    sessionName = payload.sessionName,
                )
            }

            is ServerPayload.SeatConfirmed -> {
                cachedSeatToken = payload.seatToken
                cachedPlayerId = payload.playerId
            }

            is ServerPayload.GameSnapshot -> {
                val pId = cachedPlayerId ?: payload.snapshot.privateState.playerId
                val sToken = cachedSeatToken ?: ""
                _sessionState.value = ClientSessionState.InGame(
                    snapshot = payload.snapshot,
                    myPlayerId = pId,
                    seatToken = sToken,
                )
            }

            is ServerPayload.ActionAck -> {
                // Action acknowledged
            }

            is ServerPayload.ActionNack -> {
                // Action rejected by server
            }

            is ServerPayload.Error -> {
                _sessionState.value = ClientSessionState.Error(payload.message)
            }
        }
    }

    fun claimSeat(requestedPlayerId: String) {
        clientScope.launch {
            val claimMsg = ClientMessage(
                actionId = "claim-${UUID.randomUUID()}",
                payload = ClientPayload.ClaimSeat(
                    requestedPlayerId = requestedPlayerId,
                    pairingToken = targetPairingSecret,
                ),
            )
            webSocketSession?.send(json.encodeToString(claimMsg))
        }
    }

    fun sendAction(actionType: ActionType, targetAmount: Long = 0L) {
        val token = cachedSeatToken ?: return
        clientScope.launch {
            val actionMsg = ClientMessage(
                actionId = "act-${UUID.randomUUID()}",
                seatToken = token,
                payload = ClientPayload.RequestAction(
                    actionType = actionType,
                    targetAmount = targetAmount,
                ),
            )
            webSocketSession?.send(json.encodeToString(actionMsg))
        }
    }

    fun disconnect() {
        activeJob?.cancel()
        activeJob = null
        clientScope.launch {
            try {
                webSocketSession?.close()
            } catch (_: Exception) {
            }
            webSocketSession = null
        }
        _sessionState.value = ClientSessionState.Disconnected
    }
}
