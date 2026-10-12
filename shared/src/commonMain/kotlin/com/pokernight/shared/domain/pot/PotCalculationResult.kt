package com.pokernight.shared.domain.pot

import com.pokernight.shared.model.PlayerId
import com.pokernight.shared.model.Pot
import kotlinx.serialization.Serializable

@Serializable
data class PotCalculationResult(
    val pots: List<Pot>,
    val uncalledExcessRefunds: Map<PlayerId, Long> = emptyMap(),
    val totalContestedChips: Long,
    val totalRefundedChips: Long,
) {
    init {
        require(totalContestedChips >= 0L) { "Contested chips cannot be negative" }
        require(totalRefundedChips >= 0L) { "Refunded chips cannot be negative" }
    }
}
