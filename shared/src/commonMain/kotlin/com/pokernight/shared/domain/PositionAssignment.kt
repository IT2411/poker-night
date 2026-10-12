package com.pokernight.shared.domain

import com.pokernight.shared.model.PlayerId
import kotlinx.serialization.Serializable

@Serializable
data class PositionAssignment(
    val dealerPlayerId: PlayerId,
    val smallBlindPlayerId: PlayerId,
    val bigBlindPlayerId: PlayerId,
    val firstToActPreFlopId: PlayerId,
    val isHeadsUp: Boolean,
)
