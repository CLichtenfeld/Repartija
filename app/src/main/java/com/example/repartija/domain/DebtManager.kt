package com.example.repartija.domain

import com.example.repartija.data.Debt
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max

object DebtManager {

    /**
     * Recorre día por día desde lastInterestDate + 1 hasta currentDate 
     * aplicando interés diario capitalizable sobre el saldo actual.
     */
    fun updateDailyInterest(debt: Debt, currentDate: LocalDate): Debt {
        if (currentDate.isBefore(debt.lastInterestDate) || currentDate == debt.lastInterestDate) {
            return debt
        }

        val daysBetween = ChronoUnit.DAYS.between(debt.lastInterestDate, currentDate).toInt()
        val dailyRate = debt.annualRate / 100.0 / 365.0
        
        var newBalance = debt.remainingBalance
        
        for (i in 1..daysBetween) {
            val dailyInterest = newBalance * dailyRate
            newBalance += dailyInterest
        }

        debt.remainingBalance = newBalance
        debt.lastInterestDate = currentDate
        
        return debt
    }

    /**
     * Primero actualiza intereses hasta la fecha del pago, 
     * luego resta el monto del saldo (sin permitir negativo).
     */
    fun applyPayment(debt: Debt, amount: Double, date: LocalDate): Debt {
        // 1. Actualizar intereses hasta el día del pago
        updateDailyInterest(debt, date)
        
        // 2. Restar el pago
        debt.remainingBalance = max(0.0, debt.remainingBalance - amount)
        
        return debt
    }
}
