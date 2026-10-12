package com.pokernight.shared.domain.betting

import com.pokernight.shared.model.ActionEvent
import com.pokernight.shared.model.Hand
import com.pokernight.shared.model.PlayerId
import kotlinx.serialization.Serializable

@Serializable
data class BettingState(
    val hand: Hand,
    val bigBlind: Long,
    val currentLiveBet: Long,
    val minRaiseIncrement: Long,
    val activePlayerId: PlayerId?,
    val playersWhoActed: Set<PlayerId> = emptySet(),
    val isRoundComplete: Boolean = false,
    val isHandCompleteByFold: Boolean = false,
    val actionHistory: List<ActionEvent> = emptyList(),
)
