package com.pokernight.shared.domain

import com.pokernight.shared.domain.pot.PotCalculator
import com.pokernight.shared.model.Card
import com.pokernight.shared.model.ContributorAmount
import com.pokernight.shared.model.Hand
import com.pokernight.shared.model.HandId
import com.pokernight.shared.model.HandPlayer
import com.pokernight.shared.model.HandStatus
import com.pokernight.shared.model.PlayerId
import com.pokernight.shared.model.PotType
import com.pokernight.shared.model.SessionId
import com.pokernight.shared.model.Street
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PotCalculatorTest {

    private val pA = PlayerId("playerA")
    private val pB = PlayerId("playerB")
    private val pC = PlayerId("playerC")
    private val pD = PlayerId("playerD")

    private fun createTestHand(players: List<HandPlayer>): Hand = Hand(
        id = HandId("h1"),
        sessionId = SessionId("s1"),
        handNumber = 1,
        dealerPlayerId = players.first().playerId,
        smallBlindPlayerId = players.first().playerId,
        bigBlindPlayerId = players[1].playerId,
        currentStreet = Street.RIVER,
        status = HandStatus.IN_PROGRESS,
        communityCards = listOf(
            Card.parse("2h"),
            Card.parse("3d"),
            Card.parse("4c"),
            Card.parse("5s"),
            Card.parse("9h"),
        ),
        players = players,
        startedAt = 1000L,
    )

    @Test
    fun testScenarioA_FourPlayersDifferentContributions() {
        val players = listOf(
            HandPlayer(pA, 0, startingStack = 50L, remainingStack = 0L, totalContribution = 50L, streetContribution = 0L, allIn = true),
            HandPlayer(pB, 1, startingStack = 120L, remainingStack = 0L, totalContribution = 120L, streetContribution = 0L, allIn = true),
            HandPlayer(pC, 2, startingStack = 500L, remainingStack = 300L, totalContribution = 200L, streetContribution = 0L),
            HandPlayer(pD, 3, startingStack = 500L, remainingStack = 300L, totalContribution = 200L, streetContribution = 0L),
        )

        val result = PotCalculator.calculatePots(createTestHand(players))

        assertEquals(3, result.pots.size)
        assertEquals(0L, result.totalRefundedChips)
        assertEquals(570L, result.totalContestedChips)

        // Main pot: 200 chips, eligible: A, B, C, D
        val mainPot = result.pots[0]
        assertEquals(PotType.MAIN, mainPot.potType)
        assertEquals(200L, mainPot.amount)
        assertEquals(setOf(pA, pB, pC, pD), mainPot.eligiblePlayerIds)

        // Side pot 1: 210 chips, eligible: B, C, D
        val sidePot1 = result.pots[1]
        assertEquals(PotType.SIDE, sidePot1.potType)
        assertEquals(210L, sidePot1.amount)
        assertEquals(setOf(pB, pC, pD), sidePot1.eligiblePlayerIds)

        // Side pot 2: 160 chips, eligible: C, D
        val sidePot2 = result.pots[2]
        assertEquals(PotType.SIDE, sidePot2.potType)
        assertEquals(160L, sidePot2.amount)
        assertEquals(setOf(pC, pD), sidePot2.eligiblePlayerIds)
    }

    @Test
    fun testTwoPlayersAllInWithFoldedAmountsProducesSinglePot() {
        // Player A folded pre-flop with 10
        // Player B folded on flop with 40
        // Player C and D both all in for 200
        val players = listOf(
            HandPlayer(pA, 0, startingStack = 500L, remainingStack = 490L, totalContribution = 10L, folded = true),
            HandPlayer(pB, 1, startingStack = 500L, remainingStack = 460L, totalContribution = 40L, folded = true),
            HandPlayer(pC, 2, startingStack = 200L, remainingStack = 0L, totalContribution = 200L, allIn = true),
            HandPlayer(pD, 3, startingStack = 200L, remainingStack = 0L, totalContribution = 200L, allIn = true),
        )

        val result = PotCalculator.calculatePots(createTestHand(players))

        // MUST BE EXACTLY 1 POT because C and D are the only eligible players for the entire contested amount!
        assertEquals(1, result.pots.size)
        val singlePot = result.pots[0]
        assertEquals(PotType.MAIN, singlePot.potType)
        assertEquals(450L, singlePot.amount) // 10 + 40 + 200 + 200 = 450
        assertEquals(setOf(pC, pD), singlePot.eligiblePlayerIds)
    }

    @Test
    fun testScenarioC_UncalledExcessRefund() {
        val players = listOf(
            HandPlayer(pA, 0, startingStack = 50L, remainingStack = 0L, totalContribution = 50L, streetContribution = 0L, allIn = true),
            HandPlayer(pB, 1, startingStack = 120L, remainingStack = 0L, totalContribution = 120L, streetContribution = 0L, allIn = true),
            HandPlayer(pC, 2, startingStack = 500L, remainingStack = 150L, totalContribution = 350L, streetContribution = 0L),
            HandPlayer(pD, 3, startingStack = 500L, remainingStack = 300L, totalContribution = 200L, streetContribution = 0L),
        )

        val result = PotCalculator.calculatePots(createTestHand(players))

        assertEquals(150L, result.totalRefundedChips)
        assertEquals(150L, result.uncalledExcessRefunds[pC])
        assertEquals(570L, result.totalContestedChips)
    }

    @Test
    fun testScenarioE_SplitPotAndOddChipDistribution() {
        val players = listOf(
            HandPlayer(pA, 0, startingStack = 500L, remainingStack = 400L, totalContribution = 100L, streetContribution = 0L),
            HandPlayer(pB, 1, startingStack = 500L, remainingStack = 400L, totalContribution = 100L, streetContribution = 0L),
        )

        val result = PotCalculator.calculatePots(createTestHand(players))
        val mainPot = result.pots[0]
        assertEquals(200L, mainPot.amount)

        val payouts = PotCalculator.distributePot(
            pot = mainPot,
            winnerPlayerIds = listOf(pA, pB),
            dealerPlayerId = pA,
            playersClockwise = players,
        )

        assertEquals(2, payouts.size)
        assertEquals(100L, payouts[0].amount)
        assertEquals(100L, payouts[1].amount)

        val oddPot = mainPot.copy(
            amount = 101L,
            contributors = listOf(ContributorAmount(pA, 101L)),
        )
        val oddPayouts = PotCalculator.distributePot(
            pot = oddPot,
            winnerPlayerIds = listOf(pA, pB),
            dealerPlayerId = pA,
            playersClockwise = players,
        )

        val pAPayout = oddPayouts.first { it.playerId == pA }.amount
        val pBPayout = oddPayouts.first { it.playerId == pB }.amount
        assertEquals(50L, pAPayout)
        assertEquals(51L, pBPayout)
        assertEquals(101L, pAPayout + pBPayout)
    }

    @Test
    fun testIneligiblePlayerCannotBeAwardedPot() {
        val players = listOf(
            HandPlayer(pA, 0, startingStack = 50L, remainingStack = 0L, totalContribution = 50L, streetContribution = 0L, allIn = true),
            HandPlayer(pB, 1, startingStack = 500L, remainingStack = 300L, totalContribution = 200L, streetContribution = 0L),
            HandPlayer(pC, 2, startingStack = 500L, remainingStack = 300L, totalContribution = 200L, streetContribution = 0L),
        )

        val result = PotCalculator.calculatePots(createTestHand(players))
        val sidePot = result.pots[1]

        assertFailsWith<IllegalArgumentException> {
            PotCalculator.distributePot(
                pot = sidePot,
                winnerPlayerIds = listOf(pA),
                dealerPlayerId = pA,
                playersClockwise = players,
            )
        }
    }
}
