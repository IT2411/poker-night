package com.pokernight.shared.domain

import com.pokernight.shared.model.ActionType
import com.pokernight.shared.model.HandId
import com.pokernight.shared.model.HandStatus
import com.pokernight.shared.model.PlayerId
import com.pokernight.shared.model.Session
import com.pokernight.shared.model.SessionId
import com.pokernight.shared.model.SessionPlayer
import com.pokernight.shared.model.Street
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HandInitializerTest {
    @Test
    fun testStandardBlindPostingAndPotSummary() {
        val p1 = PlayerId("p1")
        val p2 = PlayerId("p2")
        val p3 = PlayerId("p3")

        val session =
            Session(
                id = SessionId("s1"),
                name = "Test Session",
                createdAt = 1000L,
                smallBlind = 10L,
                bigBlind = 20L,
                dealerPlayerId = p1,
                players =
                listOf(
                    SessionPlayer(p1, 0, startingStack = 500L, currentStack = 500L),
                    SessionPlayer(p2, 1, startingStack = 500L, currentStack = 500L),
                    SessionPlayer(p3, 2, startingStack = 500L, currentStack = 500L),
                ),
            )

        val result =
            HandInitializer.initializeHand(
                session = session,
                handId = HandId("h1"),
                handNumber = 1,
                dealerId = p1,
                timestamp = 1000L,
            )

        val hand = result.hand
        assertEquals(HandStatus.IN_PROGRESS, hand.status)
        assertEquals(Street.PRE_FLOP, hand.currentStreet)
        assertEquals(30L, hand.totalPotChips) // 10 (SB) + 20 (BB)

        // Action events verification
        assertEquals(2, result.initialActions.size)
        val sbAction = result.initialActions[0]
        assertEquals(ActionType.POST_SMALL_BLIND, sbAction.actionType)
        assertEquals(10L, sbAction.amount)
        assertEquals(p2, sbAction.playerId)

        val bbAction = result.initialActions[1]
        assertEquals(ActionType.POST_BIG_BLIND, bbAction.actionType)
        assertEquals(20L, bbAction.amount)
        assertEquals(p3, bbAction.playerId)

        // Verify player stack deductions
        val sbPlayer = hand.players.first { it.playerId == p2 }
        assertEquals(490L, sbPlayer.remainingStack)
        assertEquals(10L, sbPlayer.totalContribution)

        val bbPlayer = hand.players.first { it.playerId == p3 }
        assertEquals(480L, bbPlayer.remainingStack)
        assertEquals(20L, bbPlayer.totalContribution)

        val dealerPlayer = hand.players.first { it.playerId == p1 }
        assertEquals(500L, dealerPlayer.remainingStack)
        assertEquals(0L, dealerPlayer.totalContribution)
    }

    @Test
    fun testShortStackBlindPostingMarksPlayerAllIn() {
        val p1 = PlayerId("p1")
        val p2 = PlayerId("p2")
        val p3 = PlayerId("p3")

        // p2 has only 5 chips (less than required SB of 10)
        // p3 has only 15 chips (less than required BB of 20)
        val session =
            Session(
                id = SessionId("s1"),
                name = "Short Stack Session",
                createdAt = 1000L,
                smallBlind = 10L,
                bigBlind = 20L,
                dealerPlayerId = p1,
                players =
                listOf(
                    SessionPlayer(p1, 0, startingStack = 500L, currentStack = 500L),
                    SessionPlayer(p2, 1, startingStack = 5L, currentStack = 5L),
                    SessionPlayer(p3, 2, startingStack = 15L, currentStack = 15L),
                ),
            )

        val result =
            HandInitializer.initializeHand(
                session = session,
                handId = HandId("h1"),
                handNumber = 1,
                dealerId = p1,
                timestamp = 1000L,
            )

        val hand = result.hand
        val sbPlayer = hand.players.first { it.playerId == p2 }
        val bbPlayer = hand.players.first { it.playerId == p3 }

        // SB posts all 5 remaining chips
        assertEquals(5L, sbPlayer.totalContribution)
        assertEquals(0L, sbPlayer.remainingStack)
        assertTrue(sbPlayer.allIn)

        // BB posts all 15 remaining chips
        assertEquals(15L, bbPlayer.totalContribution)
        assertEquals(0L, bbPlayer.remainingStack)
        assertTrue(bbPlayer.allIn)

        // Total pot must equal 5 + 15 = 20
        assertEquals(20L, hand.totalPotChips)
        assertFalse(hand.players.first { it.playerId == p1 }.allIn)
    }
}
