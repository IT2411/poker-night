package com.pokernight.shared.domain

import com.pokernight.shared.domain.betting.BettingEngine
import com.pokernight.shared.domain.betting.PlayerActionRequest
import com.pokernight.shared.domain.progression.StreetManager
import com.pokernight.shared.model.ActionType
import com.pokernight.shared.model.Card
import com.pokernight.shared.model.HandId
import com.pokernight.shared.model.HandStatus
import com.pokernight.shared.model.PlayerId
import com.pokernight.shared.model.Session
import com.pokernight.shared.model.SessionId
import com.pokernight.shared.model.SessionPlayer
import com.pokernight.shared.model.Street
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreetManagerTest {
    private fun setupThreePlayerGame(): Pair<Session, InitializedHandResult> {
        val p1 = PlayerId("p1")
        val p2 = PlayerId("p2")
        val p3 = PlayerId("p3")

        val session =
            Session(
                id = SessionId("s1"),
                name = "Street Test",
                createdAt = 1000L,
                smallBlind = 10L,
                bigBlind = 20L,
                dealerPlayerId = p1,
                players =
                    listOf(
                        SessionPlayer(p1, 0, 500L, 500L),
                        SessionPlayer(p2, 1, 500L, 500L),
                        SessionPlayer(p3, 2, 500L, 500L),
                    ),
            )

        // p1 = Dealer, p2 = SB (10), p3 = BB (20)
        val init = HandInitializer.initializeHand(session, HandId("h1"), 1, p1, 1000L)
        return session to init
    }

    @Test
    fun testCompleteHandStreetProgressionFromPreFlopToShowdown() {
        val (session, init) = setupThreePlayerGame()
        val p1 = PlayerId("p1")
        val p2 = PlayerId("p2")
        val p3 = PlayerId("p3")

        var state = BettingEngine.createPreFlopState(init.hand, session.bigBlind, p1, init.initialActions)

        // 1. PRE-FLOP BETTING: p1 calls 20, p2 calls 10 (reaches 20), p3 checks (BB option)
        state = BettingEngine.applyAction(state, PlayerActionRequest(p1, ActionType.CALL), 1001L)
        state = BettingEngine.applyAction(state, PlayerActionRequest(p2, ActionType.CALL), 1002L)
        state = BettingEngine.applyAction(state, PlayerActionRequest(p3, ActionType.CHECK), 1003L)
        assertTrue(state.isRoundComplete)
        assertEquals(60L, state.hand.totalPotChips)

        // 2. ADVANCE TO FLOP (3 cards: Ah, Kd, Qs)
        val flop = listOf(Card.parse("Ah"), Card.parse("Kd"), Card.parse("Qs"))
        state = StreetManager.advanceToFlop(state, flop)

        assertEquals(Street.FLOP, state.hand.currentStreet)
        assertEquals(3, state.hand.communityCards.size)
        assertEquals(0L, state.currentLiveBet)
        // Street contributions must be reset to 0, while total contribution remains 20 each
        state.hand.players.forEach {
            assertEquals(0L, it.streetContribution)
            assertEquals(20L, it.totalContribution)
        }
        // First to act post-flop is p2 (Small Blind, first active clockwise after Dealer p1)
        assertEquals(p2, state.activePlayerId)

        // FLOP BETTING: everyone checks through (p2 checks, p3 checks, p1 checks)
        state = BettingEngine.applyAction(state, PlayerActionRequest(p2, ActionType.CHECK), 1004L)
        state = BettingEngine.applyAction(state, PlayerActionRequest(p3, ActionType.CHECK), 1005L)
        state = BettingEngine.applyAction(state, PlayerActionRequest(p1, ActionType.CHECK), 1006L)
        assertTrue(state.isRoundComplete)

        // 3. ADVANCE TO TURN (1 card: Jc)
        state = StreetManager.advanceToTurn(state, Card.parse("Jc"))
        assertEquals(Street.TURN, state.hand.currentStreet)
        assertEquals(4, state.hand.communityCards.size)
        assertEquals(p2, state.activePlayerId)

        // TURN BETTING: check-through
        state = BettingEngine.applyAction(state, PlayerActionRequest(p2, ActionType.CHECK), 1007L)
        state = BettingEngine.applyAction(state, PlayerActionRequest(p3, ActionType.CHECK), 1008L)
        state = BettingEngine.applyAction(state, PlayerActionRequest(p1, ActionType.CHECK), 1009L)
        assertTrue(state.isRoundComplete)

        // 4. ADVANCE TO RIVER (1 card: 10d)
        state = StreetManager.advanceToRiver(state, Card.parse("10d"))
        assertEquals(Street.RIVER, state.hand.currentStreet)
        assertEquals(5, state.hand.communityCards.size)
        assertEquals(p2, state.activePlayerId)

        // RIVER BETTING: check-through
        state = BettingEngine.applyAction(state, PlayerActionRequest(p2, ActionType.CHECK), 1010L)
        state = BettingEngine.applyAction(state, PlayerActionRequest(p3, ActionType.CHECK), 1011L)
        state = BettingEngine.applyAction(state, PlayerActionRequest(p1, ActionType.CHECK), 1012L)
        assertTrue(state.isRoundComplete)

        // 5. ADVANCE TO SHOWDOWN
        state = StreetManager.advanceToShowdown(state)
        assertEquals(Street.SHOWDOWN, state.hand.currentStreet)
        assertEquals(HandStatus.SHOWDOWN, state.hand.status)
        assertNull(state.activePlayerId)
    }

    @Test
    fun testCannotAdvanceStreetWhenBettingRoundIsPending() {
        val (session, init) = setupThreePlayerGame()
        val p1 = PlayerId("p1")
        val state = BettingEngine.createPreFlopState(init.hand, session.bigBlind, p1, init.initialActions)

        // Action is still pending on p1 (round NOT complete)
        assertFalse(state.isRoundComplete)
        assertFailsWith<IllegalArgumentException> {
            StreetManager.advanceToFlop(state, listOf(Card.parse("2c"), Card.parse("3d"), Card.parse("4h")))
        }
    }

    @Test
    fun testAllInRunoutAutomaticallySkipsBettingAndAllowsCardDeals() {
        val p1 = PlayerId("p1")
        val p2 = PlayerId("p2")

        val session =
            Session(
                id = SessionId("s1"),
                name = "AllIn Test",
                createdAt = 1000L,
                smallBlind = 10L,
                bigBlind = 20L,
                dealerPlayerId = p1,
                players =
                    listOf(
                        SessionPlayer(p1, 0, 50L, 50L),
                        SessionPlayer(p2, 1, 50L, 50L),
                    ),
            )

        val init = HandInitializer.initializeHand(session, HandId("h1"), 1, p1, 1000L)
        var state = BettingEngine.createPreFlopState(init.hand, session.bigBlind, p1, init.initialActions)

        // p1 goes all-in (commits remaining 40 chips, total 50)
        state = BettingEngine.applyAction(state, PlayerActionRequest(p1, ActionType.ALL_IN), 1001L)
        // p2 calls all-in (commits remaining 30 chips, total 50)
        state = BettingEngine.applyAction(state, PlayerActionRequest(p2, ActionType.CALL), 1002L)

        // Both players are all in!
        assertTrue(state.isRoundComplete)
        assertTrue(StreetManager.isAllInRunout(state.hand))

        // Runout: Deal Flop without any bets
        state = StreetManager.advanceToFlop(state, listOf(Card.parse("Ah"), Card.parse("Kh"), Card.parse("Qh")))
        assertTrue(state.isRoundComplete)
        assertNull(state.activePlayerId)

        // Deal Turn
        state = StreetManager.advanceToTurn(state, Card.parse("Jh"))
        assertTrue(state.isRoundComplete)
        assertNull(state.activePlayerId)

        // Deal River
        state = StreetManager.advanceToRiver(state, Card.parse("10h"))
        assertTrue(state.isRoundComplete)
        assertNull(state.activePlayerId)

        // Showdown
        state = StreetManager.advanceToShowdown(state)
        assertEquals(Street.SHOWDOWN, state.hand.currentStreet)
    }

    @Test
    fun testFoldWinPreventsAdvancingToNextStreet() {
        val (session, init) = setupThreePlayerGame()
        val p1 = PlayerId("p1")
        val p2 = PlayerId("p2")
        var state = BettingEngine.createPreFlopState(init.hand, session.bigBlind, p1, init.initialActions)

        // p1 folds, p2 folds -> Hand won by p3
        state = BettingEngine.applyAction(state, PlayerActionRequest(p1, ActionType.FOLD), 1001L)
        state = BettingEngine.applyAction(state, PlayerActionRequest(p2, ActionType.FOLD), 1002L)

        assertTrue(state.isHandCompleteByFold)
        assertFailsWith<IllegalArgumentException> {
            StreetManager.advanceToFlop(state, listOf(Card.parse("2c"), Card.parse("3d"), Card.parse("4h")))
        }
    }
}
