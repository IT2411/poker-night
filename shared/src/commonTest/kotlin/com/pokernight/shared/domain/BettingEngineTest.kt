package com.pokernight.shared.domain

import com.pokernight.shared.domain.betting.BettingEngine
import com.pokernight.shared.domain.betting.PlayerActionRequest
import com.pokernight.shared.model.ActionType
import com.pokernight.shared.model.HandId
import com.pokernight.shared.model.PlayerId
import com.pokernight.shared.model.Session
import com.pokernight.shared.model.SessionId
import com.pokernight.shared.model.SessionPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BettingEngineTest {

    private fun setupThreePlayerGame(): Pair<Session, InitializedHandResult> {
        val p1 = PlayerId("p1")
        val p2 = PlayerId("p2")
        val p3 = PlayerId("p3")

        val session = Session(
            id = SessionId("s1"),
            name = "Engine Test",
            createdAt = 1000L,
            smallBlind = 10L,
            bigBlind = 20L,
            dealerPlayerId = p1,
            players = listOf(
                SessionPlayer(p1, 0, startingStack = 500L, currentStack = 500L),
                SessionPlayer(p2, 1, startingStack = 500L, currentStack = 500L),
                SessionPlayer(p3, 2, startingStack = 500L, currentStack = 500L),
            ),
        )

        val init = HandInitializer.initializeHand(session, HandId("h1"), 1, p1, 1000L)
        return session to init
    }

    @Test
    fun testScenario1And2_CheckWhenFreeAndCheckWhenOwingChips() {
        val (session, init) = setupThreePlayerGame()
        val p1 = PlayerId("p1")
        val p3 = PlayerId("p3")

        val state = BettingEngine.createPreFlopState(
            hand = init.hand,
            smallBlind = session.smallBlind,
            bigBlind = session.bigBlind,
            firstToActId = init.positions.firstToActPreFlopId,
            blindActions = init.initialActions,
        )

        val legalP1 = BettingEngine.getLegalActions(state, p1)
        assertFalse(legalP1.canCheck)
        assertFailsWith<IllegalArgumentException> {
            BettingEngine.applyAction(state, PlayerActionRequest(p1, ActionType.CHECK), 1001L)
        }

        val stateAfterCall = BettingEngine.applyAction(state, PlayerActionRequest(p1, ActionType.CALL), 1002L)
        val p2 = PlayerId("p2")
        val stateAfterP2Call = BettingEngine.applyAction(stateAfterCall, PlayerActionRequest(p2, ActionType.CALL), 1003L)

        assertEquals(p3, stateAfterP2Call.activePlayerId)
        val legalP3 = BettingEngine.getLegalActions(stateAfterP2Call, p3)
        assertTrue(legalP3.canCheck)

        val stateAfterCheck = BettingEngine.applyAction(stateAfterP2Call, PlayerActionRequest(p3, ActionType.CHECK), 1004L)
        assertTrue(stateAfterCheck.isRoundComplete)
        assertNull(stateAfterCheck.activePlayerId)
    }

    @Test
    fun testScenario3And4_CallAndRaiseUpdatesLiveBet() {
        val (session, init) = setupThreePlayerGame()
        val p1 = PlayerId("p1")
        val state = BettingEngine.createPreFlopState(init.hand, session.smallBlind, session.bigBlind, p1, init.initialActions)

        val stateAfterRaise = BettingEngine.applyAction(
            state,
            PlayerActionRequest(p1, ActionType.RAISE, targetAmount = 60L),
            1005L,
        )

        assertEquals(60L, stateAfterRaise.currentLiveBet)
        val p2 = PlayerId("p2")
        assertEquals(p2, stateAfterRaise.activePlayerId)

        val legalP2 = BettingEngine.getLegalActions(stateAfterRaise, p2)
        assertEquals(50L, legalP2.callAmount)
    }

    @Test
    fun testScenario5_IllegalRaiseBelowMinimumDifferenceRejected() {
        val (session, init) = setupThreePlayerGame()
        val p1 = PlayerId("p1")
        val state = BettingEngine.createPreFlopState(init.hand, session.smallBlind, session.bigBlind, p1, init.initialActions)

        // Live bet is 20, min increment is 5 -> minimum raise-to is 25!
        val legalP1 = BettingEngine.getLegalActions(state, p1)
        assertEquals(25L, legalP1.minRaiseTo)

        // Raising to 22 (less than 25) must fail!
        assertFailsWith<IllegalArgumentException> {
            BettingEngine.applyAction(state, PlayerActionRequest(p1, ActionType.RAISE, targetAmount = 22L), 1006L)
        }

        assertEquals(20L, state.currentLiveBet)
        assertEquals(p1, state.activePlayerId)
    }

    @Test
    fun testScenario6_ShortAllInCallCommitsEntireRemainingStack() {
        val p1 = PlayerId("p1")
        val p2 = PlayerId("p2")

        val session = Session(
            id = SessionId("s1"),
            name = "Short Call Test",
            createdAt = 1000L,
            smallBlind = 10L,
            bigBlind = 20L,
            dealerPlayerId = p1,
            players = listOf(
                SessionPlayer(p1, 0, startingStack = 500L, currentStack = 500L),
                SessionPlayer(p2, 1, startingStack = 30L, currentStack = 30L),
            ),
        )

        val init = HandInitializer.initializeHand(session, HandId("h1"), 1, p1, 1000L)
        val state = BettingEngine.createPreFlopState(init.hand, session.smallBlind, session.bigBlind, p1, init.initialActions)

        val stateAfterRaise = BettingEngine.applyAction(state, PlayerActionRequest(p1, ActionType.RAISE, 100L), 1001L)

        val legalP2 = BettingEngine.getLegalActions(stateAfterRaise, p2)
        assertTrue(legalP2.isCallAllIn)
        assertEquals(10L, legalP2.callAmount)

        val stateAfterCall = BettingEngine.applyAction(stateAfterRaise, PlayerActionRequest(p2, ActionType.CALL), 1002L)
        val p2Player = stateAfterCall.hand.players.first { it.playerId == p2 }
        assertTrue(p2Player.allIn)
        assertEquals(0L, p2Player.remainingStack)
        assertEquals(30L, p2Player.totalContribution)
        assertTrue(stateAfterCall.isRoundComplete)
    }

    @Test
    fun testScenario7_FoldWinsImmediatelyWhenOnlyOnePlayerRemains() {
        val (session, init) = setupThreePlayerGame()
        val p1 = PlayerId("p1")
        val p2 = PlayerId("p2")
        val state = BettingEngine.createPreFlopState(init.hand, session.smallBlind, session.bigBlind, p1, init.initialActions)

        val stateAfterP1Fold = BettingEngine.applyAction(state, PlayerActionRequest(p1, ActionType.FOLD), 1001L)
        assertEquals(p2, stateAfterP1Fold.activePlayerId)
        assertFalse(stateAfterP1Fold.isHandCompleteByFold)

        val stateAfterP2Fold = BettingEngine.applyAction(stateAfterP1Fold, PlayerActionRequest(p2, ActionType.FOLD), 1002L)
        assertTrue(stateAfterP2Fold.isHandCompleteByFold)
        assertTrue(stateAfterP2Fold.isRoundComplete)
        assertNull(stateAfterP2Fold.activePlayerId)
    }

    @Test
    fun testScenario8_FullRaiseReopensAction() {
        val (session, init) = setupThreePlayerGame()
        val p1 = PlayerId("p1")
        val p2 = PlayerId("p2")
        val p3 = PlayerId("p3")

        val state = BettingEngine.createPreFlopState(init.hand, session.smallBlind, session.bigBlind, p1, init.initialActions)

        val s1 = BettingEngine.applyAction(state, PlayerActionRequest(p1, ActionType.CALL), 1001L)
        val s2 = BettingEngine.applyAction(s1, PlayerActionRequest(p2, ActionType.CALL), 1002L)
        val s3 = BettingEngine.applyAction(s2, PlayerActionRequest(p3, ActionType.RAISE, 60L), 1003L)

        assertEquals(p1, s3.activePlayerId)
        assertFalse(s3.isRoundComplete)
    }
}
