package com.pokernight.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class PotId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "PotId cannot be blank" }
    }
}

@Serializable
data class PayoutId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "PayoutId cannot be blank" }
    }
}

@Serializable
enum class PotType {
    MAIN,
    SIDE,
}

@Serializable
enum class PotStatus {
    CONTESTED,
    AWARDED,
    REFUNDED,
}

@Serializable
data class ContributorAmount(
    val playerId: PlayerId,
    val amount: Long,
) {
    init {
        require(amount > 0L) { "Contributor amount must be strictly positive" }
    }
}

@Serializable
data class Pot(
    val id: PotId,
    val handId: HandId,
    val potType: PotType,
    val layerIndex: Int = 0,
    val amount: Long,
    val eligiblePlayerIds: Set<PlayerId>,
    val contributors: List<ContributorAmount>,
    val status: PotStatus = PotStatus.CONTESTED,
) {
    init {
        require(layerIndex >= 0) { "Layer index cannot be negative" }
        require(amount > 0L) { "Pot amount must be strictly positive: $amount" }
        require(contributors.isNotEmpty()) { "A pot must have at least one contributor" }
        require(amount == contributors.sumOf { it.amount }) {
            "Pot amount ($amount) must exactly equal sum of contributor amounts (${contributors.sumOf { it.amount }})"
        }
    }
}

@Serializable
enum class PayoutType {
    OUTRIGHT_WIN,
    SPLIT_POT,
    UNCONTESTED_FOLD,
    ODD_CHIP,
}

@Serializable
data class Payout(
    val id: PayoutId,
    val handId: HandId,
    val potId: PotId,
    val playerId: PlayerId,
    val amount: Long,
    val payoutType: PayoutType,
) {
    init {
        require(amount > 0L) { "Payout amount must be positive: $amount" }
    }
}
