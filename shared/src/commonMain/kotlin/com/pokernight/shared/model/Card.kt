package com.pokernight.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class Suit(
    val symbol: Char,
    val code: Char,
) {
    SPADES('♠', 's'),
    HEARTS('♥', 'h'),
    DIAMONDS('♦', 'd'),
    CLUBS('♣', 'c'),
    ;

    companion object {
        fun fromChar(char: Char): Suit =
            entries.firstOrNull { it.code.equals(char, ignoreCase = true) || it.symbol == char }
                ?: throw IllegalArgumentException("Invalid suit character: $char")
    }
}

@Serializable
enum class Rank(
    val symbol: String,
    val value: Int,
) {
    TWO("2", 2),
    THREE("3", 3),
    FOUR("4", 4),
    FIVE("5", 5),
    SIX("6", 6),
    SEVEN("7", 7),
    EIGHT("8", 8),
    NINE("9", 9),
    TEN("T", 10),
    JACK("J", 11),
    QUEEN("Q", 12),
    KING("K", 13),
    ACE("A", 14),
    ;

    companion object {
        fun fromString(str: String): Rank {
            val upper = str.uppercase()
            return entries.firstOrNull { it.symbol == upper || (it == TEN && upper == "10") }
                ?: throw IllegalArgumentException("Invalid rank symbol: $str")
        }
    }
}

@Serializable
data class Card(
    val rank: Rank,
    val suit: Suit,
) {
    override fun toString(): String = "${rank.symbol}${suit.code}"

    companion object {
        fun parse(shorthand: String): Card {
            val trimmed = shorthand.trim()
            require(trimmed.length in 2..3) { "Invalid card shorthand: $trimmed" }

            val rankPart = if (trimmed.length == 3) trimmed.substring(0, 2) else trimmed.substring(0, 1)
            val suitPart = trimmed.last()

            return Card(
                rank = Rank.fromString(rankPart),
                suit = Suit.fromChar(suitPart),
            )
        }

        fun validateNoDuplicates(cards: List<Card>) {
            val unique = cards.toSet()
            require(unique.size == cards.size) {
                "Duplicate cards detected in list: ${cards.groupBy { it }.filter { it.value.size > 1 }.keys}"
            }
        }
    }
}
