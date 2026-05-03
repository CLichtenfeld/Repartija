package com.example.repartija.data.repository

import com.example.repartija.data.model.Expense
import com.example.repartija.data.model.ExpenseSplit
import com.example.repartija.data.model.Payment
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.min

data class PairBalance(
    val fromUserId: String,
    val toUserId: String,
    val amount: Double
)

@Singleton
class BalanceRepository @Inject constructor() {

    /**
     * Calculates simplified debts from expenses, splits and payments.
     * Uses the greedy algorithm to minimize the number of transactions.
     */
    fun calculateSimplifiedDebts(
        expenses: List<Expense>,
        splits: List<ExpenseSplit>,
        payments: List<Payment>
    ): List<PairBalance> {
        val balances = mutableMapOf<String, Double>()

        // Expenses -> payer gets credited
        for (e in expenses) {
            balances[e.paidBy] = (balances[e.paidBy] ?: 0.0) + e.amount
        }

        // Splits -> user gets debited
        for (s in splits) {
            balances[s.userId] = (balances[s.userId] ?: 0.0) - s.amount
        }

        // Payments -> fromUser gets credited, toUser gets debited
        for (p in payments) {
            balances[p.fromUser] = (balances[p.fromUser] ?: 0.0) + p.amount
            balances[p.toUser] = (balances[p.toUser] ?: 0.0) - p.amount
        }

        // Greedy simplification
        val creditors = balances.filter { it.value > 0.01 }
            .map { it.key to it.value }
            .sortedByDescending { it.second }
            .toMutableList()
        val debtors = balances.filter { it.value < -0.01 }
            .map { it.key to abs(it.value) }
            .sortedByDescending { it.second }
            .toMutableList()

        val simplified = mutableListOf<PairBalance>()
        var cIdx = 0
        var dIdx = 0

        while (cIdx < creditors.size && dIdx < debtors.size) {
            val creditor = creditors[cIdx]
            val debtor = debtors[dIdx]
            val amount = min(creditor.second, debtor.second)

            if (amount > 0.01) {
                simplified.add(
                    PairBalance(
                        fromUserId = debtor.first,
                        toUserId = creditor.first,
                        amount = amount
                    )
                )
            }

            creditors[cIdx] = creditor.copy(second = creditor.second - amount)
            debtors[dIdx] = debtor.copy(second = debtor.second - amount)

            if (creditors[cIdx].second < 0.01) cIdx++
            if (debtors[dIdx].second < 0.01) dIdx++
        }

        return simplified
    }
}
