package com.pokernight.shared.domain

import com.pokernight.shared.domain.evaluation.HandCategory
import com.pokernight.shared.domain.evaluation.HandEvaluator
import com.pokernight.shared.model.Card
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FullHouseShowdownTest {

    @Test
    fun testNinesFullOfFivesBeatsFivesFullOfAces() {
        // Board: 9d, 5c, 5s, 2h, 3c
        val board = listOf(
            Card.parse("9d"),
            Card.parse("5c"),
            Card.parse("5s"),
            Card.parse("2h"),
            Card.parse("3c"),
        )

        // Player 1 Hole Cards: 9s, 9h -> Best 5 cards: 9s, 9h, 9d, 5c, 5s (99955)
        val p1Cards = listOf(Card.parse("9s"), Card.parse("9h")) + board
        val p1Hand = HandEvaluator.evaluate7Cards(p1Cards)
        assertEquals(HandCategory.FULL_HOUSE, p1Hand.category)

        // Player 2 Hole Cards: 5h, 5d -> Best 5 cards: 5h, 5d, 5c, 9s, 9d (55599)
        // Or if Player 2 had Aces: Ah, Ad on board 5c 5s 5d 2h 3c -> (555AA)
        val p2Board = listOf(Card.parse("5c"), Card.parse("5s"), Card.parse("5d"), Card.parse("2h"), Card.parse("3c"))
        val p2Cards = listOf(Card.parse("Ah"), Card.parse("Ad")) + p2Board
        val p2Hand = HandEvaluator.evaluate7Cards(p2Cards)
        assertEquals(HandCategory.FULL_HOUSE, p2Hand.category)

        // In poker rules: 99955 > 555AA because trips rank 9 > trips rank 5!
        assertTrue(p1Hand > p2Hand, "Nines full of Fives must beat Fives full of Aces")
    }
}
