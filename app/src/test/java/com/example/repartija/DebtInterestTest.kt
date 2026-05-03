package com.example.repartija

import com.example.repartija.data.Debt
import com.example.repartija.domain.DebtManager
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DebtInterestTest {

    @Test
    fun testDailyInterestAndPayment() {
        val startDate = LocalDate.of(2023, 1, 1)
        
        // Deuda inicial: $1000, TNA 30%
        var debt = Debt(
            id = 1,
            description = "Test Debt",
            fromMemberId = 1,
            toMemberId = 2,
            originalAmount = 1000.0,
            currency = "ARS",
            remainingBalance = 1000.0,
            annualRate = 30.0,
            startDate = startDate,
            lastInterestDate = startDate
        )

        // Día 1: saldo = $1000.82
        debt = DebtManager.updateDailyInterest(debt, startDate.plusDays(1))
        assertEquals(1000.8219, debt.remainingBalance, 0.001)

        // Día 2: saldo = $1001.64
        debt = DebtManager.updateDailyInterest(debt, startDate.plusDays(2))
        assertEquals(1001.6445, debt.remainingBalance, 0.001)

        // Pago de $500 el día 2 → saldo = $501.64
        debt = DebtManager.applyPayment(debt, 500.0, startDate.plusDays(2))
        assertEquals(501.6445, debt.remainingBalance, 0.001)

        // Día 3: interés sobre $501.64 → saldo = $502.05
        debt = DebtManager.updateDailyInterest(debt, startDate.plusDays(3))
        // Cálculo: 501.6445 * (1 + 0.3/365) = 501.6445 * 1.0008219 = 502.056
        assertEquals(502.056, debt.remainingBalance, 0.01)
    }
}
