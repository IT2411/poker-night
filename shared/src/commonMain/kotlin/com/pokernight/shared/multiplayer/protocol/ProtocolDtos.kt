package com.pokernight.shared.multiplayer.protocol

import com.pokernight.shared.model.Card
import com.pokernight.shared.model.HandStatus
import com.pokernight.shared.model.Street
import kotlinx.serialization.Serializable

@Serializable
data class CardDto(
    val rank: String,
    val suit: Char,
    val display: String,
) {
    companion object {
        fun fromDomain(card: Card): CardDto = CardDto(
            rank = card.rank.symbol,
            suit = card.suit.code,
            display = card.toString(),
        )
    }
}

@Serializable
data class PublicPlayerDto(
    val playerId: String,
    val displayName: String,
    val seatIndex: Int,
    val remainingStack: Long,
    val totalContribution: Long,
    val streetContribution: Long,
    val isFolded: Boolean,
    val isAllIn: Boolean,
    val isDealer: Boolean,
    val isTurn: Boolean,
    val revealedHoleCards: List<CardDto>? = null,
)

@Serializable
data class TablePublicStateDto(
    val handNumber: Int,
    val street: Street,
    val status: HandStatus,
    val communityCards: List<CardDto>,
    val totalPotChips: Long,
    val currentLiveBet: Long,
    val minRaiseIncrement: Long,
    val activePlayerId: String?,
    val dealerPlayerId: String,
    val players: List<PublicPlayerDto>,
)

@Serializable
data class LegalActionsDto(
    val canCheck: Boolean,
    val canFold: Boolean,
    val canCall: Boolean,
    val callAmount: Long = 0L,
    val isCallAllIn: Boolean = false,
    val canBet: Boolean = false,
    val minBetAmount: Long = 0L,
    val maxBetAmount: Long = 0L,
    val canRaise: Boolean = false,
    val minRaiseTo: Long = 0L,
    val maxRaiseTo: Long = 0L,
    val canAllIn: Boolean = false,
    val allInAmount: Long = 0L,
)

@Serializable
data class PlayerPrivateStateDto(
    val playerId: String,
    val seatIndex: Int,
    val holeCards: List<CardDto>?,
    val isYourTurn: Boolean,
    val legalActions: LegalActionsDto?,
)

@Serializable
data class SanitizedGameStateSnapshot(
    val stateRevision: Long,
    val table: TablePublicStateDto,
    val privateState: PlayerPrivateStateDto,
)

@Serializable
data class AvailableSeatDto(
    val playerId: String,
    val displayName: String,
    val seatIndex: Int,
    val isOccupied: Boolean,
)
