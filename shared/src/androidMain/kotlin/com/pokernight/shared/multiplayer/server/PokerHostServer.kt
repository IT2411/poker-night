package com.pokernight.shared.multiplayer.server

import com.pokernight.shared.domain.betting.BettingEngine
import com.pokernight.shared.domain.betting.BettingState
import com.pokernight.shared.domain.betting.PlayerActionRequest
import com.pokernight.shared.model.PlayerId
import com.pokernight.shared.model.Session
import com.pokernight.shared.multiplayer.protocol.AvailableSeatDto
import com.pokernight.shared.multiplayer.protocol.ClientMessage
import com.pokernight.shared.multiplayer.protocol.ClientPayload
import com.pokernight.shared.multiplayer.protocol.ServerMessage
import com.pokernight.shared.multiplayer.protocol.ServerPayload
import com.pokernight.shared.multiplayer.sanitizer.StateSanitizer
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PokerHostServer(
    val port: Int = 8080,
    val roomId: String = "ROOM-${UUID.randomUUID().toString().take(6).uppercase()}",
    val pairingSecret: String = UUID.randomUUID().toString(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private val sessionRegistry = ClientSessionRegistry(json)
    private var engine: EmbeddedServer<*, *>? = null
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var currentSession: Session? = null
    private var currentBettingState: BettingState? = null
    private var stateRevision: Long = 1L
    private var lastRecordedHandNumber: Int = -1

    var hostPlayerId: PlayerId? = null
    var webAssetProvider: ((path: String) -> ByteArray?)? = null
    var apkProvider: (() -> ByteArray?)? = null

    // Real-time synchronization callback to update Android Host UI when a client acts
    var onClientActionApplied: ((BettingState) -> Unit)? = null

    private val processedActions = ConcurrentHashMap<String, ServerPayload.ActionAck>()

    private val _connectedCount = MutableStateFlow(0)
    val connectedCount: StateFlow<Int> = _connectedCount.asStateFlow()

    val activeBettingState: BettingState?
        get() = currentBettingState

    fun updateGameState(session: Session, bettingState: BettingState?) {
        this.currentSession = session
        this.currentBettingState = bettingState

        val handNumber = bettingState?.hand?.handNumber ?: -1
        if (handNumber != lastRecordedHandNumber) {
            processedActions.clear()
            lastRecordedHandNumber = handNumber
        }

        this.stateRevision++
        broadcastSanitizedState()
    }

    fun start() {
        if (engine != null) return

        engine = embeddedServer(CIO, port = port) {
            install(ContentNegotiation) {
                json(json)
            }
            install(WebSockets)

            routing {
                get("/health") {
                    call.respondText(
                        """{"status":"ok","roomId":"$roomId","revision":$stateRevision}""",
                        ContentType.Application.Json,
                    )
                }

                get("/lobby") {
                    val session = currentSession
                    if (session == null) {
                        call.respond(HttpStatusCode.ServiceUnavailable, "No active session")
                        return@get
                    }
                    val occupiedPlayers = (sessionRegistry.getAllClients().mapNotNull { it.playerId } + listOfNotNull(hostPlayerId)).toSet()
                    val guestSeats = session.players.filter { it.seatIndex != 0 }.map { sp ->
                        AvailableSeatDto(
                            playerId = sp.playerId.value,
                            displayName = sp.playerId.value.substringAfterLast("-"),
                            seatIndex = sp.seatIndex,
                            isOccupied = occupiedPlayers.contains(sp.playerId),
                        )
                    }
                    val lobby: ServerPayload = ServerPayload.RoomLobby(
                        roomId = roomId,
                        sessionName = session.name,
                        availableSeats = guestSeats,
                        requiresPairingToken = true,
                    )
                    call.respondText(
                        json.encodeToString<ServerPayload>(lobby),
                        ContentType.Application.Json,
                    )
                }

                get("/download/poker-night.apk") {
                    val apkBytes = apkProvider?.invoke()
                    if (apkBytes != null) {
                        call.response.header(
                            HttpHeaders.ContentDisposition,
                            ContentDisposition.Attachment
                                .withParameter(ContentDisposition.Parameters.FileName, "poker-night.apk")
                                .toString(),
                        )
                        call.respondBytes(apkBytes, ContentType.Application.OctetStream)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "APK not available")
                    }
                }

                get("/") {
                    serveWebAsset("index.html", ContentType.Text.Html)
                }

                get("/join") {
                    serveWebAsset("index.html", ContentType.Text.Html)
                }

                get("/style.css") {
                    serveWebAsset("style.css", ContentType.Text.CSS)
                }

                get("/app.js") {
                    serveWebAsset("app.js", ContentType.Application.JavaScript)
                }

                webSocket("/ws/poker") {
                    val connectionId = UUID.randomUUID().toString()
                    sessionRegistry.registerConnection(connectionId, this)
                    _connectedCount.value = sessionRegistry.getAllClients().size

                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                handleClientMessage(connectionId, text, this)
                            }
                        }
                    } finally {
                        sessionRegistry.unregisterConnection(connectionId)
                        _connectedCount.value = sessionRegistry.getAllClients().size
                        broadcastSanitizedState()
                    }
                }
            }
        }.start(wait = false)
    }

    private suspend fun io.ktor.server.routing.RoutingContext.serveWebAsset(
        fileName: String,
        contentType: ContentType,
    ) {
        val bytes = webAssetProvider?.invoke(fileName)
        if (bytes != null) {
            call.respondBytes(bytes, contentType)
        } else {
            call.respondText(
                "<!DOCTYPE html><html><head><title>Poker Night</title></head><body><h3>Poker Night Web Client</h3></body></html>",
                ContentType.Text.Html,
            )
        }
    }

    fun stop() {
        engine?.stop(1000, 2000)
        engine = null
        processedActions.clear()
    }

    private suspend fun handleClientMessage(
        connectionId: String,
        rawText: String,
        session: DefaultWebSocketServerSession,
    ) {
        val clientMsg = try {
            json.decodeFromString<ClientMessage>(rawText)
        } catch (_: Exception) {
            sessionRegistry.sendMessage(
                session,
                ServerMessage(
                    stateRevision = stateRevision,
                    errorMessage = "Malformed JSON message",
                    payload = ServerPayload.Error("Malformed JSON message"),
                ),
            )
            return
        }

        when (val payload = clientMsg.payload) {
            is ClientPayload.ClaimSeat -> {
                if (payload.pairingToken != pairingSecret) {
                    sessionRegistry.sendMessage(
                        session,
                        ServerMessage(
                            stateRevision = stateRevision,
                            ackActionId = clientMsg.actionId,
                            errorMessage = "Invalid pairing token",
                            payload = ServerPayload.ActionNack(clientMsg.actionId, "Invalid pairing token"),
                        ),
                    )
                    return
                }

                val pId = PlayerId(payload.requestedPlayerId)
                val sessionObj = currentSession
                val targetPlayer = sessionObj?.players?.firstOrNull { it.playerId == pId }

                if (targetPlayer == null || targetPlayer.seatIndex == 0) {
                    sessionRegistry.sendMessage(
                        session,
                        ServerMessage(
                            stateRevision = stateRevision,
                            ackActionId = clientMsg.actionId,
                            errorMessage = "Seat 1 is reserved exclusively for the Table Host",
                            payload = ServerPayload.ActionNack(clientMsg.actionId, "Seat 1 is reserved exclusively for the Table Host"),
                        ),
                    )
                    return
                }

                val newSeatToken = UUID.randomUUID().toString()
                sessionRegistry.bindSeat(connectionId, newSeatToken, pId)

                sessionRegistry.sendMessage(
                    session,
                    ServerMessage(
                        stateRevision = stateRevision,
                        ackActionId = clientMsg.actionId,
                        payload = ServerPayload.SeatConfirmed(
                            seatToken = newSeatToken,
                            playerId = pId.value,
                            seatIndex = targetPlayer.seatIndex,
                        ),
                    ),
                )

                broadcastSanitizedState()
            }

            is ClientPayload.RequestAction -> {
                val cachedAck = processedActions[clientMsg.actionId]
                if (cachedAck != null) {
                    sessionRegistry.sendMessage(
                        session,
                        ServerMessage(
                            stateRevision = stateRevision,
                            ackActionId = clientMsg.actionId,
                            payload = cachedAck,
                        ),
                    )
                    return
                }

                val seatToken = clientMsg.seatToken
                val pId = seatToken?.let { sessionRegistry.getPlayerIdForSeatToken(it) }

                if (pId == null) {
                    sessionRegistry.sendMessage(
                        session,
                        ServerMessage(
                            stateRevision = stateRevision,
                            ackActionId = clientMsg.actionId,
                            errorMessage = "Unauthorized seat token",
                            payload = ServerPayload.ActionNack(clientMsg.actionId, "Unauthorized seat token"),
                        ),
                    )
                    return
                }

                val curBettingState = currentBettingState
                if (curBettingState == null || curBettingState.activePlayerId != pId) {
                    sessionRegistry.sendMessage(
                        session,
                        ServerMessage(
                            stateRevision = stateRevision,
                            ackActionId = clientMsg.actionId,
                            errorMessage = "Not your turn to act",
                            payload = ServerPayload.ActionNack(clientMsg.actionId, "Not your turn to act"),
                        ),
                    )
                    return
                }

                try {
                    val req = PlayerActionRequest(pId, payload.actionType, payload.targetAmount)
                    val updated = BettingEngine.applyAction(curBettingState, req, System.currentTimeMillis())
                    currentBettingState = updated
                    stateRevision++

                    val ack = ServerPayload.ActionAck(clientMsg.actionId, updated.actionHistory.size)
                    processedActions[clientMsg.actionId] = ack

                    sessionRegistry.sendMessage(
                        session,
                        ServerMessage(
                            stateRevision = stateRevision,
                            ackActionId = clientMsg.actionId,
                            payload = ack,
                        ),
                    )
                    broadcastSanitizedState()

                    // Instantly notify and update the Host Android screen!
                    onClientActionApplied?.invoke(updated)
                } catch (e: Exception) {
                    sessionRegistry.sendMessage(
                        session,
                        ServerMessage(
                            stateRevision = stateRevision,
                            ackActionId = clientMsg.actionId,
                            errorMessage = e.message,
                            payload = ServerPayload.ActionNack(clientMsg.actionId, e.message ?: "Illegal action"),
                        ),
                    )
                }
            }

            is ClientPayload.Reconnect -> {
                val seatToken = clientMsg.seatToken
                val pId = seatToken?.let { sessionRegistry.getPlayerIdForSeatToken(it) }

                if (pId != null) {
                    sessionRegistry.bindSeat(connectionId, seatToken, pId)
                    val sessionObj = currentSession
                    val seatIndex = sessionObj?.players?.firstOrNull { it.playerId == pId }?.seatIndex ?: 0

                    sessionRegistry.sendMessage(
                        session,
                        ServerMessage(
                            stateRevision = stateRevision,
                            ackActionId = clientMsg.actionId,
                            payload = ServerPayload.SeatConfirmed(
                                seatToken = seatToken,
                                playerId = pId.value,
                                seatIndex = seatIndex,
                            ),
                        ),
                    )
                    broadcastSanitizedState()
                } else {
                    sessionRegistry.sendMessage(
                        session,
                        ServerMessage(
                            stateRevision = stateRevision,
                            ackActionId = clientMsg.actionId,
                            errorMessage = "Invalid or expired seat token",
                            payload = ServerPayload.ActionNack(clientMsg.actionId, "Invalid or expired seat token"),
                        ),
                    )
                }
            }

            is ClientPayload.Heartbeat -> {
                // Heartbeat keep-alive
            }
        }
    }

    private fun broadcastSanitizedState() {
        val sessionObj = currentSession ?: return
        val bettingStateObj = currentBettingState ?: return
        val connectedIds = sessionRegistry.getAllClients().mapNotNull { it.playerId }.toSet()

        serverScope.launch {
            sessionRegistry.getAllClients().forEach { client ->
                val snapshot = StateSanitizer.createSanitizedSnapshot(
                    session = sessionObj,
                    state = bettingStateObj,
                    forPlayerId = client.playerId,
                    stateRevision = stateRevision,
                    connectedPlayerIds = connectedIds,
                )
                sessionRegistry.sendMessage(
                    client.session,
                    ServerMessage(
                        stateRevision = stateRevision,
                        payload = ServerPayload.GameSnapshot(snapshot),
                    ),
                )
            }
        }
    }
}
