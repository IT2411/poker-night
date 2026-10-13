package com.pokernight.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class LoanId(val value: String) {
    init {
        require(value.isNotBlank()) { "LoanId cannot be blank" }
    }
}

@Serializable
enum class LenderType {
    BANK,
    PLAYER,
}

@Serializable
data class LoanRecord(
    val id: LoanId,
    val sessionId: SessionId,
    val borrowerId: PlayerId,
    val lenderType: LenderType,
    val lenderPlayerId: PlayerId? = null,
    val principalAmount: Long,
    val interestPercent: Int = 0,
    val totalRepayAmount: Long,
    val createdAt: Long,
    val isRepaid: Boolean = false,
    val repaidAt: Long? = null,
) {
    init {
        require(principalAmount > 0L) { "Loan principal must be greater than zero: $principalAmount" }
        require(interestPercent in 0..10) { "Interest percent must be between 0% and 10%: $interestPercent" }
        require(totalRepayAmount >= principalAmount) { "Total repay amount must be >= principal" }
        if (lenderType == LenderType.PLAYER) {
            requireNotNull(lenderPlayerId) { "Player loans require a lenderPlayerId" }
            require(borrowerId != lenderPlayerId) { "Borrower cannot loan chips to themselves" }
        }
    }

    companion object {
        fun calculateTotalRepay(principal: Long, interestPercent: Int): Long {
            val interestChips = (principal * interestPercent) / 100L
            return principal + interestChips
        }
    }
}
