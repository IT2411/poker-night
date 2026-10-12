package com.pokernight.shared.domain.evaluation

import com.pokernight.shared.model.Card

object HandEvaluator {
    /**
     * Evaluates the best 5-card poker hand from 7 cards (2 hole cards + 5 board cards).
     */
    fun evaluate7Cards(cards: List<Card>): HandRank {
        require(cards.size == 7) { "Texas Hold'em 7-card evaluation requires exactly 7 cards. Given: ${cards.size}" }
        Card.validateNoDuplicates(cards)

        var bestRank: HandRank? = null

        // Choose 5 cards out of 7 (21 combinations)
        for (i in 0 until 7) {
            for (j in i + 1 until 7) {
                val fiveCards = cards.filterIndexed { index, _ -> index != i && index != j }
                val rank = evaluate5Cards(fiveCards)
                if (bestRank == null || rank > bestRank) {
                    bestRank = rank
                }
            }
        }
        return bestRank!!
    }

    fun evaluate5Cards(cards: List<Card>): HandRank {
        require(cards.size == 5) { "5-card evaluation requires 5 cards" }
        val sortedRanks = cards.map { it.rank.value }.sortedDescending()
        val isFlush = cards.map { it.suit }.distinct().size == 1

        val straightHigh = getStraightHighRank(sortedRanks)
        val isStraight = straightHigh != null

        // Royal Flush / Straight Flush
        if (isFlush && isStraight) {
            return if (straightHigh == 14) {
                HandRank(HandCategory.ROYAL_FLUSH, listOf(14), "Royal Flush")
            } else {
                HandRank(HandCategory.STRAIGHT_FLUSH, listOf(straightHigh), "Straight Flush, High $straightHigh")
            }
        }

        val rankCounts =
            cards
                .groupBy { it.rank.value }
                .mapValues { it.value.size }
                .toList()
                .sortedWith(compareByDescending<Pair<Int, Int>> { it.second }.thenByDescending { it.first })

        // Four of a kind
        if (rankCounts[0].second == 4) {
            val quad = rankCounts[0].first
            val kicker = rankCounts[1].first
            return HandRank(HandCategory.FOUR_OF_A_KIND, listOf(quad, kicker), "Four of a Kind ($quad)")
        }

        // Full house
        if (rankCounts[0].second == 3 && rankCounts[1].second == 2) {
            val trips = rankCounts[0].first
            val pair = rankCounts[1].first
            return HandRank(HandCategory.FULL_HOUSE, listOf(trips, pair), "Full House ($trips full of $pair)")
        }

        // Flush
        if (isFlush) {
            return HandRank(HandCategory.FLUSH, sortedRanks, "Flush, High ${sortedRanks[0]}")
        }

        // Straight
        if (isStraight) {
            return HandRank(HandCategory.STRAIGHT, listOf(straightHigh), "Straight, High $straightHigh")
        }

        // Three of a kind
        if (rankCounts[0].second == 3) {
            val trips = rankCounts[0].first
            val kickers = listOf(rankCounts[1].first, rankCounts[2].first)
            return HandRank(HandCategory.THREE_OF_A_KIND, listOf(trips) + kickers, "Three of a Kind ($trips)")
        }

        // Two pair
        if (rankCounts[0].second == 2 && rankCounts[1].second == 2) {
            val highPair = rankCounts[0].first
            val lowPair = rankCounts[1].first
            val kicker = rankCounts[2].first
            return HandRank(HandCategory.TWO_PAIR, listOf(highPair, lowPair, kicker), "Two Pair ($highPair and $lowPair)")
        }

        // One pair
        if (rankCounts[0].second == 2) {
            val pair = rankCounts[0].first
            val kickers = listOf(rankCounts[1].first, rankCounts[2].first, rankCounts[3].first)
            return HandRank(HandCategory.ONE_PAIR, listOf(pair) + kickers, "One Pair ($pair)")
        }

        // High card
        return HandRank(HandCategory.HIGH_CARD, sortedRanks, "High Card ${sortedRanks[0]}")
    }

    private fun getStraightHighRank(sortedRanksDesc: List<Int>): Int? {
        val unique = sortedRanksDesc.distinct()
        if (unique.size != 5) return null

        // Standard straight (e.g. 14, 13, 12, 11, 10 or 9, 8, 7, 6, 5)
        if (unique[0] - unique[4] == 4) {
            return unique[0]
        }
        // Ace-low straight (Wheel: A, 5, 4, 3, 2 -> 14, 5, 4, 3, 2)
        if (unique == listOf(14, 5, 4, 3, 2)) {
            return 5
        }
        return null
    }
}
