package com.pokernight.shared.domain

import com.pokernight.shared.domain.evaluation.HandCategory
import com.pokernight.shared.domain.evaluation.HandEvaluator
import com.pokernight.shared.domain.pot.PotCalculator
import com.pokernight.shared.domain.showdown.HandResolutionManager
import com.pokernight.shared.model.Card
import com.pokernight.shared.model.Hand
import com.pokernight.shared.model.HandId
import com.pokernight.shared.model.HandPlayer
import com.pokernight.shared.model.HandStatus
import com.pokernight.shared.model.PlayerId
import com.pokernight.shared.model.Session
import com.pokernight.shared.model.SessionId
import com.pokernight.shared.model.SessionPlayer
import com.pokernight.shared.model.Street
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HandResolutionTest {
    private val p1 = PlayerId("p1")
    private val p2 = PlayerId("p2")

    private fun createStandardSession(): Session =
        Session(
            id = SessionId("s1"),
            name = "Resolution Test",
            createdAt = 1000L,
            smallBlind = 10L,
            bigBlind = 20L,
            dealerPlayerId = p1,
            players =
                listOf(
                    SessionPlayer(p1, 0, 500L, 500L),
                    SessionPlayer(p2, 1, 500L, 500L),
                ),
        )

    @Test
    fun testHandEvaluatorRanksCorrectly() {
        val royalFlush =
            listOf(
                Card.parse("Ah"),
                Card.parse("Kh"),
                Card.parse("Qh"),
                Card.parse("Jh"),
                Card.parse("10h"),
                Card.parse("2c"),
                Card.parse("3d"),
            )
        val rfRank = HandEvaluator.evaluate7Cards(royalFlush)
        assertEquals(HandCategory.ROYAL_FLUSH, rfRank.category)

        val fullHouse =
            listOf(
                Card.parse("8h"),
                Card.parse("8d"),
                Card.parse("8s"),
                Card.parse("Kc"),
                Card.parse("Kd"),
                Card.parse("2c"),
                Card.parse("3s"),
            )
        val fhRank = HandEvaluator.evaluate7Cards(fullHouse)
        assertEquals(HandCategory.FULL_HOUSE, fhRank.category)

        assertTrue(rfRank > fhRank)
    }

    @Test
    fun testManualWinnerSelectionWithoutHoleCards() {
        val session = createStandardSession()
        val hand =
            Hand(
                id = HandId("h1"),
                sessionId = session.id,
                handNumber = 1,
                dealerPlayerId = p1,
                smallBlindPlayerId = p1,
                bigBlindPlayerId = p2,
                currentStreet = Street.RIVER,
                status = HandStatus.IN_PROGRESS,
                communityCards =
                    listOf(
                        Card.parse("2c"),
                        Card.parse("3d"),
                        Card.parse("4h"),
                        Card.parse("5s"),
                        Card.parse("9c"),
                    ),
                players =
                    listOf(
                        HandPlayer(p1, 0, 500L, 300L, totalContribution = 200L, streetContribution = 0L),
                        HandPlayer(p2, 1, 500L, 300L, totalContribution = 200L, streetContribution = 0L),
                    ),
                startedAt = 1000L,
            )

        val potResult = PotCalculator.calculatePots(hand)
        val mainPot = potResult.pots[0]

        // Host manually awards pot to p1
        val result =
            HandResolutionManager.resolveWithManualWinners(
                hand = hand,
                session = session,
                winnersByPot = mapOf(mainPot.id to listOf(p1)),
                timestamp = 2000L,
            )

        assertEquals(HandStatus.COMPLETED, result.completedHand.status)
        assertEquals(2000L, result.completedHand.completedAt)

        // p1 gets 300 remaining + 400 pot = 700 chips
        val updatedP1 = result.updatedSessionPlayers.first { it.playerId == p1 }
        assertEquals(700L, updatedP1.currentStack)

        // p2 gets 300 remaining + 0 winnings = 300 chips
        val updatedP2 = result.updatedSessionPlayers.first { it.playerId == p2 }
        assertEquals(300L, updatedP2.currentStack)

        // Dealer rotates to p2 for next hand
        assertEquals(p2, result.nextDealerPlayerId)
    }

    @Test
    fun testAutomaticEvaluationResolvesWinners() {
        val session = createStandardSession()
        val board =
            listOf(
                Card.parse("2c"),
                Card.parse("3c"),
                Card.parse("4c"),
                Card.parse("Ks"),
                Card.parse("Qd"),
            )

        // p1 has Flush (Ac, 9c)
        // p2 has Pair of Kings (Kh, 10d)
        val hand =
            Hand(
                id = HandId("h1"),
                sessionId = session.id,
                handNumber = 1,
                dealerPlayerId = p1,
                smallBlindPlayerId = p1,
                bigBlindPlayerId = p2,
                currentStreet = Street.RIVER,
                status = HandStatus.IN_PROGRESS,
                communityCards = board,
                players =
                    listOf(
                        HandPlayer(
                            playerId = p1,
                            seatIndex = 0,
                            startingStack = 500L,
                            remainingStack = 300L,
                            totalContribution = 200L,
                            holeCards = listOf(Card.parse("Ac"), Card.parse("9c")),
                        ),
                        HandPlayer(
                            playerId = p2,
                            seatIndex = 1,
                            startingStack = 500L,
                            remainingStack = 300L,
                            totalContribution = 200L,
                            holeCards = listOf(Card.parse("Kh"), Card.parse("10d")),
                        ),
                    ),
                startedAt = 1000L,
            )

        val result = HandResolutionManager.resolveAutomatic(hand, session, 2000L)

        // p1 should win automatically with Flush
        val p1Winnings = result.payouts.filter { it.playerId == p1 }.sumOf { it.amount }
        assertEquals(400L, p1Winnings)

        val updatedP1 = result.updatedSessionPlayers.first { it.playerId == p1 }
        assertEquals(700L, updatedP1.currentStack)
    }

    @Test
    fun testCompletedHandCannotBePaidOutTwice() {
        val session = createStandardSession()
        val completedHand =
            Hand(
                id = HandId("h1"),
                sessionId = session.id,
                handNumber = 1,
                dealerPlayerId = p1,
                smallBlindPlayerId = p1,
                bigBlindPlayerId = p2,
                currentStreet = Street.SHOWDOWN,
                status = HandStatus.COMPLETED,
                communityCards =
                    listOf(
                        Card.parse("2c"),
                        Card.parse("3d"),
                        Card.parse("4h"),
                        Card.parse("5s"),
                        Card.parse("9c"),
                    ),
                players =
                    listOf(
                        HandPlayer(p1, 0, 500L, 500L),
                        HandPlayer(p2, 1, 500L, 500L),
                    ),
                startedAt = 1000L,
                completedAt = 2000L,
            )

        assertFailsWith<IllegalArgumentException> {
            HandResolutionManager.resolveWithManualWinners(
                hand = completedHand,
                session = session,
                winnersByPot = emptyMap(),
                timestamp = 3000L,
            )
        }
    }
}
