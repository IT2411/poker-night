package com.pokernight.shared.multiplayer.client

import com.pokernight.shared.domain.HandInitializer
import com.pokernight.shared.domain.betting.BettingEngine
import com.pokernight.shared.model.ActionType
import com.pokernight.shared.model.Card
import com.pokernight.shared.model.HandId
import com.pokernight.shared.model.PlayerId
import com.pokernight.shared.model.Session
import com.pokernight.shared.model.SessionId
import com.pokernight.shared.model.SessionPlayer
import com.pokernight.shared.multiplayer.server.PokerHostServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PokerMultiplayerClientTest {

    private val testPort = 8098
    private lateinit var server: PokerHostServer
    private lateinit var client: PokerMultiplayerClient

    private val p1 = PlayerId("p1-Alice")
    private val p2 = PlayerId("p2-Bob")

    @BeforeTest
    fun setup() = runBlocking {
        server = PokerHostServer(port = testPort)
        server.start()
        client = PokerMultiplayerClient()

        // Wait up to 1 second for CIO server socket to bind
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
        client.disconnect()
        server.stop()
    }

    @Test
    fun testNativeClientConnectClaimSeatAndReceiveGameSnapshot() = runBlocking {
        val session = Session(
            id = SessionId("s1"),
            name = "Native Client Session",
            createdAt = 1000L,
            smallBlind = 10L,
            bigBlind = 20L,
            dealerPlayerId = p1,
            players = listOf(
                SessionPlayer(p1, 0, 500L, 500L),
                SessionPlayer(p2, 1, 500L, 500L),
            ),
        )
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

        val joinUrl = "http://127.0.0.1:$testPort/join?room=${server.roomId}&pair=${server.pairingSecret}"
        client.connect(joinUrl)

        // Wait up to 2 seconds for WebSocket handshake and seat selection state
        var waited = 0
        while (client.sessionState.value !is ClientSessionState.SelectingSeat && waited < 20) {
            delay(100)
            waited++
        }

        val selectState = client.sessionState.value
        assertTrue(selectState is ClientSessionState.SelectingSeat)
        assertEquals(2, selectState.availableSeats.size)

        // Claim seat for Alice (p1)
        client.claimSeat(p1.value)

        waited = 0
        while (client.sessionState.value !is ClientSessionState.InGame && waited < 20) {
            delay(100)
            waited++
        }

        val inGameState = client.sessionState.value
        assertTrue(inGameState is ClientSessionState.InGame)
        assertEquals(p1.value, inGameState.myPlayerId)

        // Alice sees her own cards (Ah, Kd)
        val holeCards = inGameState.snapshot.privateState.holeCards
        assertNotNull(holeCards)
        assertEquals("A", holeCards[0].rank)

        // Alice sends a Call action
        client.sendAction(ActionType.CALL)

        delay(200)

        // Verify Bob is now the active turn player in the updated snapshot
        val updatedInGame = client.sessionState.value as ClientSessionState.InGame
        assertEquals(p2.value, updatedInGame.snapshot.table.activePlayerId)
    }
}
