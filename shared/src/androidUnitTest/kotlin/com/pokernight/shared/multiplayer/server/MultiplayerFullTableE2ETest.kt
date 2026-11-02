package com.pokernight.shared.multiplayer.server

import com.pokernight.shared.domain.HandInitializer
import com.pokernight.shared.domain.betting.BettingEngine
import com.pokernight.shared.domain.betting.BettingState
import com.pokernight.shared.domain.betting.PlayerActionRequest
import com.pokernight.shared.domain.pot.PotCalculator
import com.pokernight.shared.domain.progression.StreetManager
import com.pokernight.shared.domain.showdown.HandResolutionManager
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
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MultiplayerFullTableE2ETest {

    private val testPort = 8099
    private lateinit var server: PokerHostServer
    private val json = Json { ignoreUnknownKeys = true }

    private val p1 = PlayerId("p1-Alice")
    private val p2 = PlayerId("p2-Bob")
    private val p3 = PlayerId("p3-Charlie")
    private val p4 = PlayerId("p4-Dave")
    private val p5 = PlayerId("p5-Eve")
    private val p6 = PlayerId("p6-Frank")

    @BeforeTest
    fun setup() = runBlocking {
        server = PokerHostServer(port = testPort)
        server.start()

        val checkClient = HttpClient(CIO)
        var bound = false
        var attempts = 0
        while (!bound && attempts < 20) {
            try {
                checkClient.get("http://127.0.0.1:$testPort/health")
                bound = true
            } catch (_: Exception) {
                delay(50)
                attempts++
            }
        }
        checkClient.close()
    }

    @AfterTest
    fun tearDown() {
        server.stop()
    }

    private suspend fun DefaultClientWebSocketSession.receiveMatching(
        predicate: (ServerMessage) -> Boolean,
    ): ServerMessage {
        while (true) {
            val frame = incoming.receive() as Frame.Text
            val msg = json.decodeFromString<ServerMessage>(frame.readText())
            if (predicate(msg)) return msg
        }
    }

    private fun checkThroughStreet(initialState: BettingState, startTimestamp: Long): BettingState {
        var s = initialState
        var t = startTimestamp
        while (!s.isRoundComplete && s.activePlayerId != null) {
            s = BettingEngine.applyAction(s, PlayerActionRequest(s.activePlayerId!!, ActionType.CHECK), t++)
        }
        return s
    }

    @Test
    fun testFull6PlayerTableCompleteHandWithSecurityAndChipConservation() = runBlocking {
        // 1. Session Setup: 6 Players x 1,000 chips = 6,000 total chips
        val session = Session(
            id = SessionId("session-full-6"),
            name = "Championship Table",
            createdAt = 1000L,
            smallBlind = 10L,
            bigBlind = 20L,
            dealerPlayerId = p1,
            players = listOf(
                SessionPlayer(p1, 0, 1000L, 1000L),
                SessionPlayer(p2, 1, 1000L, 1000L),
                SessionPlayer(p3, 2, 1000L, 1000L),
                SessionPlayer(p4, 3, 1000L, 1000L),
                SessionPlayer(p5, 4, 1000L, 1000L),
                SessionPlayer(p6, 5, 1000L, 1000L),
            ),
        )

        val init = HandInitializer.initializeHand(session, HandId("h1"), 1, p1, 1000L)
        val updatedPlayers = init.hand.players.mapIndexed { idx, hp ->
            when (idx) {
                0 -> hp.copy(holeCards = listOf(Card.parse("Ah"), Card.parse("Kd")))
                1 -> hp.copy(holeCards = listOf(Card.parse("Qc"), Card.parse("Js")))
                2 -> hp.copy(holeCards = listOf(Card.parse("10d"), Card.parse("9h")))
                3 -> hp.copy(holeCards = listOf(Card.parse("8s"), Card.parse("7c")))
                4 -> hp.copy(holeCards = listOf(Card.parse("6h"), Card.parse("5d")))
                else -> hp.copy(holeCards = listOf(Card.parse("4c"), Card.parse("4s")))
            }
        }
        val hand = init.hand.copy(players = updatedPlayers)
        val state = BettingEngine.createPreFlopState(hand, session.smallBlind, session.bigBlind, p4, init.initialActions)

        server.updateGameState(session, state)

        val httpClient = HttpClient(CIO) {
            install(WebSockets)
        }

        val clients = mutableMapOf<PlayerId, DefaultClientWebSocketSession>()
        val tokens = mutableMapOf<PlayerId, String>()
        val remotePlayers = listOf(p2, p3, p4, p5, p6)

        for (player in remotePlayers) {
            val clientSession = httpClient.webSocketSession(host = "127.0.0.1", port = testPort, path = "/ws/poker")
            clients[player] = clientSession

            val claim = ClientMessage(
                actionId = "claim-${player.value}",
                payload = ClientPayload.ClaimSeat(player.value, server.pairingSecret),
            )
            clientSession.send(json.encodeToString(claim))

            val confirmMsg = clientSession.receiveMatching { it.payload is ServerPayload.SeatConfirmed }
            val seatConfirmed = confirmMsg.payload as ServerPayload.SeatConfirmed
            tokens[player] = seatConfirmed.seatToken

            val snapMsg = clientSession.receiveMatching { it.payload is ServerPayload.GameSnapshot }
            val snapshot = (snapMsg.payload as ServerPayload.GameSnapshot).snapshot

            assertNotNull(snapshot.privateState.holeCards)
            assertEquals(2, snapshot.privateState.holeCards?.size)
            snapshot.table.players.filter { it.playerId != player.value }.forEach { opponent ->
                assertNull(opponent.revealedHoleCards, "Opponent ${opponent.displayName} hole cards leaked!")
            }
        }

        assertEquals(5, server.connectedCount.value)

        // 2. Adversarial Test: Bob (p2) attempts out-of-turn bet while action is on Dave (p4)
        val illegalMsg = ClientMessage(
            actionId = "illegal-bob-act",
            seatToken = tokens[p2],
            payload = ClientPayload.RequestAction(ActionType.CALL),
        )
        clients[p2]!!.send(json.encodeToString(illegalMsg))

        val nackMsg = clients[p2]!!.receiveMatching { it.ackActionId == "illegal-bob-act" }
        assertTrue(nackMsg.payload is ServerPayload.ActionNack)
        assertEquals("Not your turn to act", (nackMsg.payload as ServerPayload.ActionNack).reason)

        // 3. Complete Pre-Flop Betting in strict clockwise turn order: p4 -> p5 -> p6 -> p1 -> p2 -> p3
        val p4Act = ClientMessage("act-p4", tokens[p4], payload = ClientPayload.RequestAction(ActionType.CALL))
        clients[p4]!!.send(json.encodeToString(p4Act))
        val p4Ack = clients[p4]!!.receiveMatching { it.ackActionId == "act-p4" }
        assertTrue(p4Ack.payload is ServerPayload.ActionAck)

        val p5Act = ClientMessage("act-p5", tokens[p5], payload = ClientPayload.RequestAction(ActionType.FOLD))
        clients[p5]!!.send(json.encodeToString(p5Act))
        val p5Ack = clients[p5]!!.receiveMatching { it.ackActionId == "act-p5" }
        assertTrue(p5Ack.payload is ServerPayload.ActionAck)

        val p6Act = ClientMessage("act-p6", tokens[p6], payload = ClientPayload.RequestAction(ActionType.CALL))
        clients[p6]!!.send(json.encodeToString(p6Act))
        val p6Ack = clients[p6]!!.receiveMatching { it.ackActionId == "act-p6" }
        assertTrue(p6Ack.payload is ServerPayload.ActionAck)

        // Alice (p1 - Host) calls 20 directly on host engine
        val curState = server.activeBettingState!!
        val stateAfterHost = BettingEngine.applyAction(
            curState,
            PlayerActionRequest(p1, ActionType.CALL),
            2001L,
        )
        server.updateGameState(session, stateAfterHost)

        val p2Act = ClientMessage("act-p2", tokens[p2], payload = ClientPayload.RequestAction(ActionType.CALL))
        clients[p2]!!.send(json.encodeToString(p2Act))
        val p2Ack = clients[p2]!!.receiveMatching { it.ackActionId == "act-p2" }
        assertTrue(p2Ack.payload is ServerPayload.ActionAck)

        val p3Act = ClientMessage("act-p3", tokens[p3], payload = ClientPayload.RequestAction(ActionType.CHECK))
        clients[p3]!!.send(json.encodeToString(p3Act))
        val p3Ack = clients[p3]!!.receiveMatching { it.ackActionId == "act-p3" }
        assertTrue(p3Ack.payload is ServerPayload.ActionAck)

        val completedPreFlopState = server.activeBettingState!!
        assertEquals(100L, completedPreFlopState.hand.totalPotChips)
        assertTrue(completedPreFlopState.isRoundComplete)

        // 4. Advance Streets: Deal Flop -> Check-through -> Deal Turn -> Check-through -> Deal River -> Check-through -> Showdown
        var currentStreetState = StreetManager.advanceToFlop(
            completedPreFlopState,
            listOf(Card.parse("2d"), Card.parse("2s"), Card.parse("3h")),
        )
        currentStreetState = checkThroughStreet(currentStreetState, 2100L)
        assertTrue(currentStreetState.isRoundComplete)

        currentStreetState = StreetManager.advanceToTurn(currentStreetState, Card.parse("3c"))
        currentStreetState = checkThroughStreet(currentStreetState, 2200L)
        assertTrue(currentStreetState.isRoundComplete)

        currentStreetState = StreetManager.advanceToRiver(currentStreetState, Card.parse("Jd"))
        currentStreetState = checkThroughStreet(currentStreetState, 2300L)
        assertTrue(currentStreetState.isRoundComplete)

        currentStreetState = StreetManager.advanceToShowdown(currentStreetState)
        server.updateGameState(session, currentStreetState)

        // 5. Showdown & Awarding Pot to Dave (p4)
        val potResult = PotCalculator.calculatePots(currentStreetState.hand)
        assertEquals(100L, potResult.pots[0].amount)

        val resolution = HandResolutionManager.resolveWithManualWinners(
            hand = currentStreetState.hand,
            session = session,
            winnersByPot = mapOf(potResult.pots[0].id to listOf(p4)),
            timestamp = 3000L,
        )

        // 6. TOTAL TABLE CHIP CONSERVATION INVARIANT: 6,000 chips strictly preserved
        val totalEndChips = resolution.updatedSessionPlayers.sumOf { it.currentStack }
        assertEquals(6000L, totalEndChips, "Table chip conservation failure! Chips must strictly equal 6000L")

        for (client in clients.values) {
            client.close()
        }
        httpClient.close()
    }
}
