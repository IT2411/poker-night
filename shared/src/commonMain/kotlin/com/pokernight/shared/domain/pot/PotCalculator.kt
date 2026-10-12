package com.pokernight.shared.domain.pot

import com.pokernight.shared.model.ContributorAmount
import com.pokernight.shared.model.Hand
import com.pokernight.shared.model.HandPlayer
import com.pokernight.shared.model.Payout
import com.pokernight.shared.model.PayoutId
import com.pokernight.shared.model.PayoutType
import com.pokernight.shared.model.PlayerId
import com.pokernight.shared.model.Pot
import com.pokernight.shared.model.PotId
import com.pokernight.shared.model.PotType

object PotCalculator {
    /**
     * Calculates Main and Side pots and uncalled excess refunds from player contributions.
     */
    fun calculatePots(hand: Hand): PotCalculationResult {
        val totalContributionsSum = hand.players.sumOf { it.totalContribution }
        if (totalContributionsSum == 0L) {
            return PotCalculationResult(emptyList(), emptyMap(), 0L, 0L)
        }

        // 1. Calculate Uncalled Excess Refund
        // Sort contributions descending
        val sortedContributions =
            hand.players
                .map { it.playerId to it.totalContribution }
                .sortedByDescending { it.second }

        val highestContrib = sortedContributions[0].second
        val secondHighestContrib = if (sortedContributions.size > 1) sortedContributions[1].second else 0L

        val refunds = mutableMapOf<PlayerId, Long>()
        val effectiveContributions = mutableMapOf<PlayerId, Long>()

        for (hp in hand.players) {
            effectiveContributions[hp.playerId] = hp.totalContribution
        }

        // If the highest contributor put in more than anyone else could match
        if (highestContrib > secondHighestContrib) {
            val uncalledExcess = highestContrib - secondHighestContrib
            val highestPlayerId = sortedContributions[0].first
            refunds[highestPlayerId] = uncalledExcess
            effectiveContributions[highestPlayerId] = secondHighestContrib
        }

        val totalRefunded = refunds.values.sum()

        // 2. Derive pot layers from positive distinct contribution levels
        val distinctLevels =
            effectiveContributions.values
                .filter { it > 0L }
                .distinct()
                .sorted()

        val pots = mutableListOf<Pot>()
        var previousLevel = 0L

        for ((index, level) in distinctLevels.withIndex()) {
            val delta = level - previousLevel
            val contributorsAtLevel = mutableListOf<ContributorAmount>()
            val eligibleAtLevel = mutableSetOf<PlayerId>()

            for (hp in hand.players) {
                val effectiveAmount = effectiveContributions[hp.playerId] ?: 0L
                if (effectiveAmount >= level) {
                    // Contributed the full delta for this layer
                    contributorsAtLevel.add(ContributorAmount(hp.playerId, delta))
                    if (!hp.folded) {
                        eligibleAtLevel.add(hp.playerId)
                    }
                } else if (effectiveAmount > previousLevel) {
                    // Partial contribution within this layer
                    val partialAmount = effectiveAmount - previousLevel
                    contributorsAtLevel.add(ContributorAmount(hp.playerId, partialAmount))
                    if (!hp.folded) {
                        eligibleAtLevel.add(hp.playerId)
                    }
                }
            }

            val potAmount = contributorsAtLevel.sumOf { it.amount }
            if (potAmount > 0L) {
                val potType = if (pots.isEmpty()) PotType.MAIN else PotType.SIDE
                pots.add(
                    Pot(
                        id = PotId("${hand.id.value}-pot-${index + 1}"),
                        handId = hand.id,
                        potType = potType,
                        layerIndex = index,
                        amount = potAmount,
                        eligiblePlayerIds = eligibleAtLevel,
                        contributors = contributorsAtLevel,
                    ),
                )
            }
            previousLevel = level
        }

        val totalContested = pots.sumOf { it.amount }

        // Invariant: Total contributions must strictly reconcile with contested + refunded
        require(totalContributionsSum == totalContested + totalRefunded) {
            "Accounting mismatch! Sum=$totalContributionsSum, Contested=$totalContested, Refunded=$totalRefunded"
        }

        return PotCalculationResult(
            pots = pots,
            uncalledExcessRefunds = refunds,
            totalContestedChips = totalContested,
            totalRefundedChips = totalRefunded,
        )
    }

    /**
     * Distributes a pot evenly among winners, awarding odd chips clockwise from the dealer button.
     */
    fun distributePot(
        pot: Pot,
        winnerPlayerIds: List<PlayerId>,
        dealerPlayerId: PlayerId,
        playersClockwise: List<HandPlayer>,
    ): List<Payout> {
        require(winnerPlayerIds.isNotEmpty()) { "Cannot distribute pot ${pot.id.value} without winners" }
        // Verify all winners are actually eligible for this pot
        val ineligibleWinners = winnerPlayerIds.filter { !pot.eligiblePlayerIds.contains(it) }
        require(ineligibleWinners.isEmpty()) {
            "Player(s) $ineligibleWinners are not eligible to win pot ${pot.id.value}"
        }

        val share = pot.amount / winnerPlayerIds.size
        var remainder = pot.amount % winnerPlayerIds.size

        // Clockwise distance from dealer to resolve odd chips (FR-41)
        val orderedWinners = sortWinnersClockwiseFromDealer(winnerPlayerIds, dealerPlayerId, playersClockwise)

        var payoutCounter = 1
        val payouts = mutableListOf<Payout>()

        for (winner in orderedWinners) {
            val oddChip =
                if (remainder > 0L) {
                    remainder--
                    1L
                } else {
                    0L
                }
            val totalWinnerAmount = share + oddChip

            if (totalWinnerAmount > 0L) {
                val payoutType =
                    when {
                        winnerPlayerIds.size > 1 && oddChip > 0L -> PayoutType.ODD_CHIP
                        winnerPlayerIds.size > 1 -> PayoutType.SPLIT_POT
                        pot.contributors.size == 1 -> PayoutType.UNCONTESTED_FOLD
                        else -> PayoutType.OUTRIGHT_WIN
                    }

                payouts.add(
                    Payout(
                        id = PayoutId("${pot.id.value}-pay-${payoutCounter++}"),
                        handId = pot.handId,
                        potId = pot.id,
                        playerId = winner,
                        amount = totalWinnerAmount,
                        payoutType = payoutType,
                    ),
                )
            }
        }

        // Strict conservation check: sum of payouts must match pot amount exactly
        val totalPaid = payouts.sumOf { it.amount }
        require(totalPaid == pot.amount) {
            "Payout sum ($totalPaid) does not equal pot amount (${pot.amount})"
        }

        return payouts
    }

    private fun sortWinnersClockwiseFromDealer(
        winnerIds: List<PlayerId>,
        dealerId: PlayerId,
        playersClockwise: List<HandPlayer>,
    ): List<PlayerId> {
        val sortedSeats = playersClockwise.sortedBy { it.seatIndex }
        val dealerIndex = sortedSeats.indexOfFirst { it.playerId == dealerId }
        val count = sortedSeats.size

        val winnerSet = winnerIds.toSet()
        val ordered = mutableListOf<PlayerId>()

        for (i in 1..count) {
            val candidate = sortedSeats[(dealerIndex + i) % count].playerId
            if (winnerSet.contains(candidate)) {
                ordered.add(candidate)
            }
        }
        return ordered
    }
}
