package com.pokernight.shared.domain.betting

import com.pokernight.shared.model.PlayerId
import kotlinx.serialization.Serializable

@Serializable
data class LegalActions(
    val playerId: PlayerId,
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
