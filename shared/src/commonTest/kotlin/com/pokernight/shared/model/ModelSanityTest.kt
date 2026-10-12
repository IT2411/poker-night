package com.pokernight.shared.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ModelSanityTest {
    private val json = Json { prettyPrint = true }

    @Test
    fun testCardParsingAndInvariants() {
        val aceOfSpades = Card.parse("As")
        assertEquals(Rank.ACE, aceOfSpades.rank)
        assertEquals(Suit.SPADES, aceOfSpades.suit)
        assertEquals("As", aceOfSpades.toString())

        val tenOfHearts = Card.parse("10h")
        assertEquals(Rank.TEN, tenOfHearts.rank)
        assertEquals(Suit.HEARTS, tenOfHearts.suit)

        assertFailsWith<IllegalArgumentException> { Card.parse("1X") }
        assertFailsWith<IllegalArgumentException> {
            Card.validateNoDuplicates(listOf(aceOfSpades, aceOfSpades))
        }
    }

    @Test
    fun testSessionPlayerClockwiseOrderingAndInvariants() {
        val p1 = PlayerId("p1")
        val p2 = PlayerId("p2")
        val p3 = PlayerId("p3")

        val sessionPlayers =
            listOf(
                SessionPlayer(playerId = p2, seatIndex = 4, startingStack = 1000L, currentStack = 1000L),
                SessionPlayer(playerId = p1, seatIndex = 1, startingStack = 1000L, currentStack = 1000L),
                SessionPlayer(playerId = p3, seatIndex = 7, startingStack = 1000L, currentStack = 1000L),
            )

        val session =
            Session(
                id = SessionId("s1"),
                name = "Home Game #1",
                createdAt = 1700000000000L,
                smallBlind = 10L,
                bigBlind = 20L,
                dealerPlayerId = p1,
                players = sessionPlayers,
            )

        val clockwise = session.getSeatedPlayersClockwise()
        assertEquals(listOf(p1, p2, p3), clockwise.map { it.playerId })
    }

    @Test
    fun testHandPlayerStackConservationInvariant() {
        val p1 = PlayerId("p1")

        // Correct: 1000 = 850 + 150
        val validPlayer =
            HandPlayer(
                playerId = p1,
                seatIndex = 0,
                startingStack = 1000L,
                remainingStack = 850L,
                totalContribution = 150L,
                streetContribution = 50L,
            )
        assertEquals(1000L, validPlayer.startingStack)

        // Invalid: 1000 != 800 + 150 (violates conservation of chips)
        assertFailsWith<IllegalArgumentException> {
            HandPlayer(
                playerId = p1,
                seatIndex = 0,
                startingStack = 1000L,
                remainingStack = 800L,
                totalContribution = 150L,
                streetContribution = 50L,
            )
        }
    }

    @Test
    fun testHandCommunityCardsValidation() {
        val p1 = PlayerId("p1")
        val p2 = PlayerId("p2")
        val handPlayers =
            listOf(
                HandPlayer(p1, 0, 1000L, 1000L),
                HandPlayer(p2, 1, 1000L, 1000L),
            )

        // Preflop with community cards is illegal
        assertFailsWith<IllegalArgumentException> {
            Hand(
                id = HandId("h1"),
                sessionId = SessionId("s1"),
                handNumber = 1,
                dealerPlayerId = p1,
                smallBlindPlayerId = p1,
                bigBlindPlayerId = p2,
                currentStreet = Street.PRE_FLOP,
                communityCards = listOf(Card.parse("Ah")),
                players = handPlayers,
                startedAt = 1700000000000L,
            )
        }
    }

    @Test
    fun testPotLayerExactAccounting() {
        val p1 = PlayerId("p1")
        val p2 = PlayerId("p2")

        val pot =
            Pot(
                id = PotId("pot-1"),
                handId = HandId("h1"),
                potType = PotType.MAIN,
                layerIndex = 0,
                amount = 200L,
                eligiblePlayerIds = setOf(p1, p2),
                contributors =
                listOf(
                    ContributorAmount(p1, 100L),
                    ContributorAmount(p2, 100L),
                ),
            )
        assertEquals(200L, pot.amount)

        // Invariant failure: contributors sum != pot amount
        assertFailsWith<IllegalArgumentException> {
            Pot(
                id = PotId("pot-2"),
                handId = HandId("h1"),
                potType = PotType.MAIN,
                layerIndex = 0,
                amount = 250L, // Mismatch!
                eligiblePlayerIds = setOf(p1, p2),
                contributors =
                listOf(
                    ContributorAmount(p1, 100L),
                    ContributorAmount(p2, 100L),
                ),
            )
        }
    }

    @Test
    fun testSerializationRoundTrip() {
        val p1 = PlayerId("p1")
        val action =
            ActionEvent(
                id = ActionId("a1"),
                handId = HandId("h1"),
                sequenceNumber = 1,
                playerId = p1,
                street = Street.PRE_FLOP,
                actionType = ActionType.POST_SMALL_BLIND,
                amount = 10L,
                toTotalStreetAmount = 10L,
                createdAt = 1700000000000L,
            )

        val serialized = json.encodeToString(action)
        val deserialized = json.decodeFromString<ActionEvent>(serialized)

        assertEquals(action, deserialized)
        assertTrue(serialized.contains("POST_SMALL_BLIND"))
    }
}
