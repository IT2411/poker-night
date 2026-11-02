package com.pokernight.shared.multiplayer.server

import com.pokernight.shared.domain.HandInitializer
import com.pokernight.shared.domain.betting.BettingEngine
import com.pokernight.shared.model.ActionType
import com.pokernight.shared.model.Card
import com.pokernight.shared.model.HandId
import com.pokernight.shared.model.PlayerId
import com.pokernight.shared.model.Session
import com.pokernight.shared.model.SessionId
import com.pokernight.shared.model.SessionPlayer
import com.pokernight.shared.multiplayer.protocol.ClientMessage
import com.pokernight.shared.multiplayer.protocol.ClientPayload
import com.pokernight.shared.multiplayer.protocol.ServerMessage
import com.pokernight.shared.multiplayer.protocol.ServerPayload
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PokerHostServerTest {

    private val testPort = 8089
    private lateinit var server: PokerHostServer
    private val json = Json { ignoreUnknownKeys = true }

    private val p1 = PlayerId("p1-Alice")
    private val p2 = PlayerId("p2-Bob")

    private fun createTestSession(): Session = Session(
        id = SessionId("s1"),
        name = "Server Test Session",
        createdAt = 1000L,
        smallBlind = 10L,
        bigBlind = 20L,
        dealerPlayerId = p1,
        players = listOf(
            SessionPlayer(p1, 0, 500L, 500L),
            SessionPlayer(p2, 1, 500L, 500L),
        ),
    )

    @BeforeTest
    fun setup() {
        server = PokerHostServer(port = testPort)
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.stop()
    }

    @Test
    fun testHealthEndpointReturnsOkAndRoomId() = runBlocking {
        val client = HttpClient(CIO)
        val response = client.get("http://127.0.0.1:$testPort/health")
        val body = response.bodyAsText()
        assertTrue(body.contains("ok"))
        assertTrue(body.contains(server.roomId))
        client.close()
    }

    @Test
    fun testWebSocketConnectClaimSeatAndSanitizedStateDelivery() = runBlocking {
        val session = createTestSession()
        val init = HandInitializer.initializeHand(session, HandId("h1"), 1, p1, 1000L)
        val updatedPlayers = init.hand.players.map { hp ->
            if (hp.playerId == p1) {
                hp.copy(holeCards = listOf(Card.parse("Ah"), Card.parse("Kd")))
            } else {
                hp.copy(holeCards = listOf(Card.parse("2c"), Card.parse("3d")))
            }
        }
        val hand = init.hand.copy(players = updatedPlayers)
        val state = BettingEngine.createPreFlopState(hand, session.smallBlind, session.bigBlind, p1, init.initialActions)

        server.updateGameState(session, state)

        val httpClient = HttpClient(CIO) {
            install(WebSockets)
        }

        httpClient.webSocket(host = "127.0.0.1", port = testPort, path = "/ws/poker") {
            // Claim Seat as Alice (p1)
            val claimMsg = ClientMessage(
                actionId = "act-1",
                payload = ClientPayload.ClaimSeat(
                    requestedPlayerId = p1.value,
                    pairingToken = server.pairingSecret,
                ),
            )
            send(json.encodeToString(claimMsg))

            // 1. Receive Seat Confirmation
            val confirmFrame = incoming.receive() as Frame.Text
            val confirmMsg = json.decodeFromString<ServerMessage>(confirmFrame.readText())
            assertTrue(confirmMsg.payload is ServerPayload.SeatConfirmed)
            val seatConfirmed = confirmMsg.payload as ServerPayload.SeatConfirmed
            assertEquals(p1.value, seatConfirmed.playerId)
            assertNotNull(seatConfirmed.seatToken)

            // 2. Receive Sanitized Game Snapshot
            val snapFrame = incoming.receive() as Frame.Text
            val snapMsg = json.decodeFromString<ServerMessage>(snapFrame.readText())
            assertTrue(snapMsg.payload is ServerPayload.GameSnapshot)
            val gameSnapshot = (snapMsg.payload as ServerPayload.GameSnapshot).snapshot

            // Verify Alice sees only her own cards (Ah, Kd)
            assertNotNull(gameSnapshot.privateState.holeCards)
            assertEquals("A", gameSnapshot.privateState.holeCards?.get(0)?.rank)

            // Verify Bob's cards in public table are strictly null!
            val bobPublic = gameSnapshot.table.players.first { it.playerId == p2.value }
            assertEquals(null, bobPublic.revealedHoleCards)

            // Alice calls 20
            val callAction = ClientMessage(
                actionId = "act-2",
                seatToken = seatConfirmed.seatToken,
                payload = ClientPayload.RequestAction(
                    actionType = ActionType.CALL,
                ),
            )
            send(json.encodeToString(callAction))

            // 3. Receive ActionAck
            val ackFrame = incoming.receive() as Frame.Text
            val ackMsg = json.decodeFromString<ServerMessage>(ackFrame.readText())
            assertTrue(ackMsg.payload is ServerPayload.ActionAck)
            val actionAck = ackMsg.payload as ServerPayload.ActionAck
            assertEquals("act-2", actionAck.actionId)
        }

        httpClient.close()
    }
}
