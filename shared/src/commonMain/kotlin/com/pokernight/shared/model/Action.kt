package com.pokernight.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class ActionId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "ActionId cannot be blank" }
    }
}

@Serializable
enum class ActionType {
    POST_SMALL_BLIND,
    POST_BIG_BLIND,
    CHECK,
    FOLD,
    CALL,
    BET,
    RAISE,
    ALL_IN,
    SHOW_CARDS,
    MUCK_CARDS,
}

@Serializable
data class ActionEvent(
    val id: ActionId,
    val handId: HandId,
    val sequenceNumber: Int,
    val playerId: PlayerId,
    val street: Street,
    val actionType: ActionType,
    val amount: Long = 0L,
    val toTotalStreetAmount: Long = 0L,
    val createdAt: Long,
    val reversalOfActionId: ActionId? = null,
) {
    init {
        require(sequenceNumber >= 1) { "Sequence number must start at 1 or greater" }
        require(amount >= 0L) { "Action amount cannot be negative: $amount" }
        require(toTotalStreetAmount >= 0L) { "toTotalStreetAmount cannot be negative: $toTotalStreetAmount" }
        require(createdAt > 0L) { "Action createdAt timestamp must be positive" }

        // Only enforce non-zero amount on live actions (not on reversal audit tombstones)
        if (reversalOfActionId == null) {
            when (actionType) {
                ActionType.CHECK, ActionType.FOLD, ActionType.MUCK_CARDS -> {
                    require(amount == 0L) { "$actionType cannot carry a chip amount" }
                }
                ActionType.POST_SMALL_BLIND,
                ActionType.POST_BIG_BLIND,
                ActionType.CALL,
                ActionType.BET,
                ActionType.RAISE,
                ActionType.ALL_IN,
                -> {
                    require(amount > 0L) { "$actionType must have a positive chip contribution amount" }
                }
                ActionType.SHOW_CARDS -> Unit
            }

            if (actionType == ActionType.RAISE || actionType == ActionType.BET) {
                require(toTotalStreetAmount >= amount) {
                    "Target street amount ($toTotalStreetAmount) must be >= incremental amount ($amount)"
                }
            }
        }
    }
}
