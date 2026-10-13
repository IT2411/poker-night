package com.pokernight.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LoanTest {

    private val p1 = PlayerId("p1")
    private val p2 = PlayerId("p2")
    private val sId = SessionId("s1")

    @Test
    fun testLoanRepayCalculationWithInterest() {
        // 0% interest
        assertEquals(1000L, LoanRecord.calculateTotalRepay(1000L, 0))

        // 5% interest on 1000 chips = 1050 chips
        assertEquals(1050L, LoanRecord.calculateTotalRepay(1000L, 5))

        // Max 10% interest on 500 chips = 550 chips
        assertEquals(550L, LoanRecord.calculateTotalRepay(500L, 10))
    }

    @Test
    fun testLoanInvariantsRejectExcessInterestOrNegativePrincipal() {
        // Reject interest > 10%
        assertFailsWith<IllegalArgumentException> {
            LoanRecord(
                id = LoanId("l1"),
                sessionId = sId,
                borrowerId = p1,
                lenderType = LenderType.BANK,
                principalAmount = 500L,
                interestPercent = 15,
                totalRepayAmount = 575L,
                createdAt = 1000L,
            )
        }

        // Reject zero or negative principal
        assertFailsWith<IllegalArgumentException> {
            LoanRecord(
                id = LoanId("l2"),
                sessionId = sId,
                borrowerId = p1,
                lenderType = LenderType.BANK,
                principalAmount = 0L,
                interestPercent = 0,
                totalRepayAmount = 0L,
                createdAt = 1000L,
            )
        }

        // Reject player loaning to themselves
        assertFailsWith<IllegalArgumentException> {
            LoanRecord(
                id = LoanId("l3"),
                sessionId = sId,
                borrowerId = p1,
                lenderType = LenderType.PLAYER,
                lenderPlayerId = p1,
                principalAmount = 100L,
                interestPercent = 0,
                totalRepayAmount = 100L,
                createdAt = 1000L,
            )
        }
    }
}
