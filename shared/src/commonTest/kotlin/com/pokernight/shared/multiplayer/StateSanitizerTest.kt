package com.pokernight.shared.multiplayer

import com.pokernight.shared.domain.HandInitializer
import com.pokernight.shared.domain.betting.BettingEngine
import com.pokernight.shared.domain.betting.BettingState
import com.pokernight.shared.model.Card
import com.pokernight.shared.model.HandId
import com.pokernight.shared.model.HandStatus
import com.pokernight.shared.model.PlayerId
import com.pokernight.shared.model.Session
import com.pokernight.shared.model.SessionId
import com.pokernight.shared.model.SessionPlayer
import com.pokernight.shared.model.Street
import com.pokernight.shared.multiplayer.sanitizer.StateSanitizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StateSanitizerTest {

    private val p1 = PlayerId("p1-Alice")
    private val p2 = PlayerId("p2-Bob")
    private val p3 = PlayerId("p3-Charlie")

    private fun createTestSession(): Session = Session(
        id = SessionId("s1"),
        name = "Security Test",
        createdAt = 1000L,
        smallBlind = 10L,
        bigBlind = 20L,
        dealerPlayerId = p1,
        players = listOf(
            SessionPlayer(p1, 0, 500L, 500L),
            SessionPlayer(p2, 1, 500L, 500L),
            SessionPlayer(p3, 2, 500L, 500L),
        ),
    )

    @Test
    fun testZeroCardLeaks_OpponentHoleCardsAreStrictlyNull() {
        val session = createTestSession()
        val init = HandInitializer.initializeHand(session, HandId("h1"), 1, p1, 1000L)

        // Assign private hole cards to Alice and Bob
        val updatedPlayers = init.hand.players.map { hp ->
            when (hp.playerId) {
                p1 -> hp.copy(holeCards = listOf(Card.parse("Ah"), Card.parse("Kd")))
                p2 -> hp.copy(holeCards = listOf(Card.parse("Qc"), Card.parse("Js")))
                else -> hp
            }
        }
        val handWithCards = init.hand.copy(players = updatedPlayers)
        val state = BettingEngine.createPreFlopState(
            hand = handWithCards,
            smallBlind = session.smallBlind,
            bigBlind = session.bigBlind,
            firstToActId = p1,
            blindActions = init.initialActions,
        )

        // 1. Snapshot generated for Alice
        val aliceSnapshot = StateSanitizer.createSanitizedSnapshot(session, state, forPlayerId = p1, stateRevision = 1L)

        // Alice's private state MUST contain Alice's cards
        assertNotNull(aliceSnapshot.privateState.holeCards)
        assertEquals(2, aliceSnapshot.privateState.holeCards?.size)
        assertEquals("A", aliceSnapshot.privateState.holeCards?.get(0)?.rank)

        // In public table list for Alice: Bob's hole cards MUST be null!
        val bobInAliceView = aliceSnapshot.table.players.first { it.playerId == p2.value }
        assertNull(bobInAliceView.revealedHoleCards, "Bob's hole cards must never be leaked to Alice!")

        // 2. Snapshot generated for Bob
        val bobSnapshot = StateSanitizer.createSanitizedSnapshot(session, state, forPlayerId = p2, stateRevision = 1L)

        // Bob's private state contains Bob's cards
        assertNotNull(bobSnapshot.privateState.holeCards)
        assertEquals("Q", bobSnapshot.privateState.holeCards?.get(0)?.rank)

        // In public table list for Bob: Alice's hole cards MUST be null!
        val aliceInBobView = bobSnapshot.table.players.first { it.playerId == p1.value }
        assertNull(aliceInBobView.revealedHoleCards, "Alice's hole cards must never be leaked to Bob!")

        // 3. Spectator Snapshot (unseated viewer)
        val spectatorSnapshot = StateSanitizer.createSanitizedSnapshot(session, state, forPlayerId = null, stateRevision = 1L)
        assertNull(spectatorSnapshot.privateState.holeCards)
        spectatorSnapshot.table.players.forEach {
            assertNull(it.revealedHoleCards, "Spectators must receive null for all player cards!")
        }
    }

    @Test
    fun testShowdownCardRevealRules() {
        val session = createTestSession()
        val init = HandInitializer.initializeHand(session, HandId("h1"), 1, p1, 1000L)

        val updatedPlayers = init.hand.players.map { hp ->
            when (hp.playerId) {
                p1 -> hp.copy(holeCards = listOf(Card.parse("Ah"), Card.parse("Kd")), folded = false)
                p2 -> hp.copy(holeCards = listOf(Card.parse("2c"), Card.parse("3s")), folded = true) // Folded
                else -> hp
            }
        }
        val showdownHand = init.hand.copy(
            currentStreet = Street.SHOWDOWN,
            status = HandStatus.SHOWDOWN,
            communityCards = listOf(
                Card.parse("4h"),
                Card.parse("5d"),
                Card.parse("6s"),
                Card.parse("7c"),
                Card.parse("8h"),
            ),
            players = updatedPlayers,
        )
        val state = BettingState(
            hand = showdownHand,
            smallBlind = session.smallBlind,
            bigBlind = session.bigBlind,
            currentLiveBet = 0L,
            minRaiseIncrement = session.bigBlind,
            activePlayerId = null,
            playersWhoActed = emptySet(),
            isRoundComplete = true,
            isHandCompleteByFold = false,
            actionHistory = init.initialActions,
        )

        // At showdown: Active non-folded player (Alice) has cards revealed publicly
        val snapshot = StateSanitizer.createSanitizedSnapshot(session, state, forPlayerId = null, stateRevision = 10L)

        val alicePublic = snapshot.table.players.first { it.playerId == p1.value }
        assertNotNull(alicePublic.revealedHoleCards)

        // Folded player (Bob) cards must STILL be null (mucked)
        val bobPublic = snapshot.table.players.first { it.playerId == p2.value }
        assertNull(bobPublic.revealedHoleCards, "Folded player cards must remain mucked at showdown!")
    }

    @Test
    fun testLegalActionsPopulatedOnlyForActiveTurnPlayer() {
        val session = createTestSession()
        val init = HandInitializer.initializeHand(session, HandId("h1"), 1, p1, 1000L)
        // First to act is p1
        val state = BettingEngine.createPreFlopState(
            hand = init.hand,
            smallBlind = session.smallBlind,
            bigBlind = session.bigBlind,
            firstToActId = p1,
            blindActions = init.initialActions,
        )

        // Alice (p1) is on turn: legal actions populated
        val aliceSnap = StateSanitizer.createSanitizedSnapshot(session, state, forPlayerId = p1, stateRevision = 1L)
        assertTrue(aliceSnap.privateState.isYourTurn)
        assertNotNull(aliceSnap.privateState.legalActions)
        assertTrue(aliceSnap.privateState.legalActions?.canCall == true)

        // Bob (p2) is NOT on turn: legal actions must be null
        val bobSnap = StateSanitizer.createSanitizedSnapshot(session, state, forPlayerId = p2, stateRevision = 1L)
        assertFalse(bobSnap.privateState.isYourTurn)
        assertNull(bobSnap.privateState.legalActions)
    }
}
