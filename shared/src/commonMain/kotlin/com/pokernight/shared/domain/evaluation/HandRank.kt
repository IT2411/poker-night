package com.pokernight.shared.domain.evaluation

import kotlinx.serialization.Serializable

@Serializable
enum class HandCategory(
    val rankScore: Int,
) {
    HIGH_CARD(1),
    ONE_PAIR(2),
    TWO_PAIR(3),
    THREE_OF_A_KIND(4),
    STRAIGHT(5),
    FLUSH(6),
    FULL_HOUSE(7),
    FOUR_OF_A_KIND(8),
    STRAIGHT_FLUSH(9),
    ROYAL_FLUSH(10),
}

@Serializable
data class HandRank(
    val category: HandCategory,
    val tieBreakers: List<Int>,
    val description: String,
) : Comparable<HandRank> {
    override fun compareTo(other: HandRank): Int {
        if (this.category.rankScore != other.category.rankScore) {
            return this.category.rankScore.compareTo(other.category.rankScore)
        }
        for (i in 0 until minOf(this.tieBreakers.size, other.tieBreakers.size)) {
            val cmp = this.tieBreakers[i].compareTo(other.tieBreakers[i])
            if (cmp != 0) return cmp
        }
        return 0
    }
}
