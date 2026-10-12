package com.pokernight.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class HandId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "HandId cannot be blank" }
    }
}

@Serializable
enum class Street {
    PRE_FLOP,
    FLOP,
    TURN,
    RIVER,
    SHOWDOWN,
}

@Serializable
enum class HandStatus {
    WAITING_FOR_BLINDS,
    IN_PROGRESS,
    SHOWDOWN,
    COMPLETED,
    CANCELLED,
}

@Serializable
data class HandPlayer(
    val playerId: PlayerId,
    val seatIndex: Int,
    val startingStack: Long,
    val remainingStack: Long,
    val totalContribution: Long = 0L,
    val streetContribution: Long = 0L,
    val folded: Boolean = false,
    val allIn: Boolean = false,
    val holeCards: List<Card>? = null,
) {
    init {
        require(seatIndex >= 0) { "Seat index must be non-negative" }
        require(startingStack >= 0L) { "Starting stack cannot be negative: $startingStack" }
        require(remainingStack >= 0L) { "Remaining stack cannot be negative: $remainingStack" }
        require(totalContribution >= 0L) { "Total contribution cannot be negative: $totalContribution" }
        require(streetContribution >= 0L) { "Street contribution cannot be negative: $streetContribution" }
        require(streetContribution <= totalContribution) {
            "Street contribution ($streetContribution) cannot exceed total contribution ($totalContribution)"
        }
        require(startingStack == remainingStack + totalContribution) {
            "Starting stack ($startingStack) must equal remaining stack ($remainingStack) + total contribution ($totalContribution)"
        }
        if (allIn) {
            require(remainingStack == 0L) { "An all-in player must have 0 remaining chips in stack" }
        }
        holeCards?.let {
            require(it.size == 2) { "Texas Hold'em requires exactly 2 hole cards if entered" }
            Card.validateNoDuplicates(it)
        }
    }
}

@Serializable
data class Hand(
    val id: HandId,
    val sessionId: SessionId,
    val handNumber: Int,
    val dealerPlayerId: PlayerId,
    val smallBlindPlayerId: PlayerId,
    val bigBlindPlayerId: PlayerId,
    val currentStreet: Street = Street.PRE_FLOP,
    val status: HandStatus = HandStatus.WAITING_FOR_BLINDS,
    val communityCards: List<Card> = emptyList(),
    val players: List<HandPlayer>,
    val startedAt: Long,
    val completedAt: Long? = null,
) {
    init {
        require(handNumber > 0) { "Hand number must be positive: $handNumber" }
        require(startedAt > 0L) { "StartedAt timestamp must be positive" }
        require(players.size >= 2) { "A hand requires at least 2 players" }

        val playerIds = players.map { it.playerId }.toSet()
        require(playerIds.size == players.size) { "Player IDs in a hand must be unique" }
        require(playerIds.contains(dealerPlayerId)) { "Dealer must participate in the hand" }
        require(playerIds.contains(smallBlindPlayerId)) { "Small blind player must participate in the hand" }
        require(playerIds.contains(bigBlindPlayerId)) { "Big blind player must participate in the hand" }

        when (currentStreet) {
            Street.PRE_FLOP -> require(communityCards.isEmpty()) { "Pre-flop cannot have community cards" }
            Street.FLOP -> require(communityCards.size == 3) { "Flop must have exactly 3 community cards" }
            Street.TURN -> require(communityCards.size == 4) { "Turn must have exactly 4 community cards" }
            Street.RIVER, Street.SHOWDOWN -> require(communityCards.size == 5) { "River/Showdown must have exactly 5 community cards" }
        }

        if (communityCards.isNotEmpty()) {
            Card.validateNoDuplicates(communityCards)
        }

        if (status == HandStatus.COMPLETED) {
            requireNotNull(completedAt) { "Completed hand must have a completedAt timestamp" }
            require(completedAt >= startedAt) { "CompletedAt must be >= startedAt" }
        }
    }

    val totalPotChips: Long
        get() = players.sumOf { it.totalContribution }
}
