package com.pokernight.shared.domain

import com.pokernight.shared.model.PlayerId
import com.pokernight.shared.model.SessionPlayer
import com.pokernight.shared.model.SessionPlayerStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DealerRotationTest {
    @Test
    fun testDealerRotationAcrossFourPlayers() {
        val p1 = PlayerId("p1")
        val p2 = PlayerId("p2")
        val p3 = PlayerId("p3")
        val p4 = PlayerId("p4")

        val players =
            listOf(
                SessionPlayer(p1, seatIndex = 0, startingStack = 1000L, currentStack = 1000L),
                SessionPlayer(p2, seatIndex = 1, startingStack = 1000L, currentStack = 1000L),
                SessionPlayer(p3, seatIndex = 2, startingStack = 1000L, currentStack = 1000L),
                SessionPlayer(p4, seatIndex = 3, startingStack = 1000L, currentStack = 1000L),
            )

        val eligible = SeatingNavigator.getEligibleActivePlayers(players)

        // Hand 1 starts with p1 as dealer
        val d1 = p1
        // Next hand dealer rotates to p2
        val d2 = SeatingNavigator.rotateDealer(eligible, d1)
        assertEquals(p2, d2)
        // Next hand rotates to p3
        val d3 = SeatingNavigator.rotateDealer(eligible, d2)
        assertEquals(p3, d3)
        // Next hand rotates to p4
        val d4 = SeatingNavigator.rotateDealer(eligible, d3)
        assertEquals(p4, d4)
        // Completes the circle back to p1
        val d5 = SeatingNavigator.rotateDealer(eligible, d4)
        assertEquals(p1, d5)
    }

    @Test
    fun testDealerRotationSkipsSittingOutAndBustedPlayers() {
        val p1 = PlayerId("p1")
        val p2 = PlayerId("p2")
        val p3 = PlayerId("p3")

        val players =
            listOf(
                SessionPlayer(p1, seatIndex = 0, startingStack = 1000L, currentStack = 1000L),
                // p2 is sitting out
                SessionPlayer(p2, seatIndex = 1, startingStack = 1000L, currentStack = 1000L, status = SessionPlayerStatus.SITTING_OUT),
                // p3 is active
                SessionPlayer(p3, seatIndex = 2, startingStack = 1000L, currentStack = 1000L),
            )

        val eligible = SeatingNavigator.getEligibleActivePlayers(players)
        assertEquals(2, eligible.size)

        // Rotating from p1 should skip p2 and land on p3
        val nextDealer = SeatingNavigator.rotateDealer(eligible, p1)
        assertEquals(p3, nextDealer)
    }

    @Test
    fun testThreePlayerPositionsAndPreFlopOrder() {
        val p1 = PlayerId("p1")
        val p2 = PlayerId("p2")
        val p3 = PlayerId("p3")

        val players =
            listOf(
                SessionPlayer(p1, seatIndex = 0, startingStack = 1000L, currentStack = 1000L),
                SessionPlayer(p2, seatIndex = 1, startingStack = 1000L, currentStack = 1000L),
                SessionPlayer(p3, seatIndex = 2, startingStack = 1000L, currentStack = 1000L),
            )

        val positions = SeatingNavigator.calculatePositions(players, dealerId = p1)
        assertEquals(p1, positions.dealerPlayerId)
        assertEquals(p2, positions.smallBlindPlayerId)
        assertEquals(p3, positions.bigBlindPlayerId)
        // In 3 players: UTG is dealer (p1)
        assertEquals(p1, positions.firstToActPreFlopId)
    }

    @Test
    fun testHeadsUpPositionsAndPreFlopOrder() {
        val p1 = PlayerId("p1")
        val p2 = PlayerId("p2")

        val players =
            listOf(
                SessionPlayer(p1, seatIndex = 0, startingStack = 1000L, currentStack = 1000L),
                SessionPlayer(p2, seatIndex = 1, startingStack = 1000L, currentStack = 1000L),
            )

        val positions = SeatingNavigator.calculatePositions(players, dealerId = p1)

        // In Heads-Up: Dealer is Small Blind and acts FIRST pre-flop
        assertTrue(positions.isHeadsUp)
        assertEquals(p1, positions.dealerPlayerId)
        assertEquals(p1, positions.smallBlindPlayerId)
        assertEquals(p2, positions.bigBlindPlayerId)
        assertEquals(p1, positions.firstToActPreFlopId)
    }
}
