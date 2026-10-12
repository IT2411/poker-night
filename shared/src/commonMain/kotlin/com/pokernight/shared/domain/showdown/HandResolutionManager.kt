package com.pokernight.shared.domain.showdown

import com.pokernight.shared.domain.SeatingNavigator
import com.pokernight.shared.domain.evaluation.HandEvaluator
import com.pokernight.shared.domain.evaluation.HandRank
import com.pokernight.shared.domain.pot.PotCalculator
import com.pokernight.shared.model.Hand
import com.pokernight.shared.model.HandStatus
import com.pokernight.shared.model.Payout
import com.pokernight.shared.model.PlayerId
import com.pokernight.shared.model.PotId
import com.pokernight.shared.model.Session
import com.pokernight.shared.model.SessionPlayer

data class HandResolutionResult(
    val completedHand: Hand,
    val payouts: List<Payout>,
    val uncalledRefunds: Map<PlayerId, Long>,
    val updatedSessionPlayers: List<SessionPlayer>,
    val nextDealerPlayerId: PlayerId,
)

object HandResolutionManager {
    /**
     * Resolves a hand using manual winner selections for each pot.
     * Works whether hole cards were entered or not (SRS FR-43).
     */
    fun resolveWithManualWinners(
        hand: Hand,
        session: Session,
        winnersByPot: Map<PotId, List<PlayerId>>,
        timestamp: Long,
    ): HandResolutionResult {
        require(hand.status != HandStatus.COMPLETED) { "Hand is already completed and cannot be paid out twice" }

        val potResult = PotCalculator.calculatePots(hand)
        val allPayouts = mutableListOf<Payout>()

        for (pot in potResult.pots) {
            val winners =
                winnersByPot[pot.id]
                    ?: throw IllegalArgumentException("Missing winner selection for pot ${pot.id.value}")

            val potPayouts =
                PotCalculator.distributePot(
                    pot = pot,
                    winnerPlayerIds = winners,
                    dealerPlayerId = hand.dealerPlayerId,
                    playersClockwise = hand.players,
                )
            allPayouts.addAll(potPayouts)
        }

        return finalizeResolution(hand, session, potResult.uncalledExcessRefunds, allPayouts, timestamp)
    }

    /**
     * Resolves a hand automatically by evaluating players' hole cards against the 5 community cards.
     */
    fun resolveAutomatic(
        hand: Hand,
        session: Session,
        timestamp: Long,
    ): HandResolutionResult {
        require(hand.status != HandStatus.COMPLETED) { "Hand is already completed and cannot be paid out twice" }
        require(hand.communityCards.size == 5) { "Automatic showdown requires 5 community cards" }

        val activePlayers = hand.players.filter { !it.folded }
        require(activePlayers.all { it.holeCards != null && it.holeCards.size == 2 }) {
            "All non-folded players must have 2 hole cards entered for automatic evaluation"
        }

        val potResult = PotCalculator.calculatePots(hand)

        // Precompute hand ranks
        val playerRanks = mutableMapOf<PlayerId, HandRank>()
        for (player in activePlayers) {
            val fullCards = player.holeCards!! + hand.communityCards
            playerRanks[player.playerId] = HandEvaluator.evaluate7Cards(fullCards)
        }

        val allPayouts = mutableListOf<Payout>()

        for (pot in potResult.pots) {
            val eligiblePlayers = pot.eligiblePlayerIds.toList()
            require(eligiblePlayers.isNotEmpty()) { "Pot ${pot.id.value} has no eligible players" }

            var bestRank: HandRank? = null
            val potWinners = mutableListOf<PlayerId>()

            for (pId in eligiblePlayers) {
                val rank = playerRanks[pId]!!
                if (bestRank == null || rank > bestRank) {
                    bestRank = rank
                    potWinners.clear()
                    potWinners.add(pId)
                } else if (rank.compareTo(bestRank) == 0) {
                    potWinners.add(pId)
                }
            }

            val potPayouts =
                PotCalculator.distributePot(
                    pot = pot,
                    winnerPlayerIds = potWinners,
                    dealerPlayerId = hand.dealerPlayerId,
                    playersClockwise = hand.players,
                )
            allPayouts.addAll(potPayouts)
        }

        return finalizeResolution(hand, session, potResult.uncalledExcessRefunds, allPayouts, timestamp)
    }

    /**
     * Resolves an uncontested fold win (all opponents folded).
     */
    fun resolveUncontestedFold(
        hand: Hand,
        session: Session,
        timestamp: Long,
    ): HandResolutionResult {
        require(hand.status != HandStatus.COMPLETED) { "Hand is already completed and cannot be paid out twice" }
        val nonFolded = hand.players.filter { !it.folded }
        require(nonFolded.size == 1) { "Uncontested fold resolution requires exactly 1 remaining non-folded player" }

        val winner = nonFolded.first()
        val potResult = PotCalculator.calculatePots(hand)
        val allPayouts = mutableListOf<Payout>()

        for (pot in potResult.pots) {
            val potPayouts =
                PotCalculator.distributePot(
                    pot = pot,
                    winnerPlayerIds = listOf(winner.playerId),
                    dealerPlayerId = hand.dealerPlayerId,
                    playersClockwise = hand.players,
                )
            allPayouts.addAll(potPayouts)
        }

        return finalizeResolution(hand, session, potResult.uncalledExcessRefunds, allPayouts, timestamp)
    }

    private fun finalizeResolution(
        hand: Hand,
        session: Session,
        uncalledRefunds: Map<PlayerId, Long>,
        payouts: List<Payout>,
        timestamp: Long,
    ): HandResolutionResult {
        // Compute updated stacks for SessionPlayers:
        // NewStack = HandRemainingStack + PayoutsWon + UncalledRefund
        val updatedSessionPlayers =
            session.players.map { sp ->
                val handPlayer = hand.players.firstOrNull { it.playerId == sp.playerId }
                if (handPlayer != null) {
                    val winnings = payouts.filter { it.playerId == sp.playerId }.sumOf { it.amount }
                    val refund = uncalledRefunds[sp.playerId] ?: 0L
                    val newStack = handPlayer.remainingStack + winnings + refund
                    sp.copy(currentStack = newStack)
                } else {
                    sp
                }
            }

        // Verify chip conservation invariant across the entire table
        val initialTableChips = hand.players.sumOf { it.startingStack }
        val finalTableChips =
            updatedSessionPlayers
                .filter { sp -> hand.players.any { it.playerId == sp.playerId } }
                .sumOf { it.currentStack }

        require(initialTableChips == finalTableChips) {
            "Table chips conservation violation! Start=$initialTableChips, End=$finalTableChips"
        }

        val completedHand =
            hand.copy(
                status = HandStatus.COMPLETED,
                completedAt = timestamp,
            )

        val eligibleForNext = SeatingNavigator.getEligibleActivePlayers(updatedSessionPlayers)
        val nextDealer =
            if (eligibleForNext.size >= 2) {
                SeatingNavigator.rotateDealer(eligibleForNext, hand.dealerPlayerId)
            } else {
                hand.dealerPlayerId
            }

        return HandResolutionResult(
            completedHand = completedHand,
            payouts = payouts,
            uncalledRefunds = uncalledRefunds,
            updatedSessionPlayers = updatedSessionPlayers,
            nextDealerPlayerId = nextDealer,
        )
    }
}
