package com.pokernight.shared.domain.betting

import com.pokernight.shared.model.ActionType
import com.pokernight.shared.model.PlayerId
import kotlinx.serialization.Serializable

@Serializable
data class PlayerActionRequest(
    val playerId: PlayerId,
    val actionType: ActionType,
    /**
     * For BET: opening bet amount.
     * For RAISE: total target street contribution ("raise to" amount).
     */
    val targetAmount: Long = 0L,
)
