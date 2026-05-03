package com.example.repartija.data.repository

import com.example.repartija.AppConstants.Tables
import com.example.repartija.data.model.Expense
import com.example.repartija.data.model.ExpenseSplit
import com.example.repartija.data.model.Payment
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class MemberTransaction(
    val id: String,
    val date: String,
    val description: String,
    val amount: Double,
    val type: TransactionType,
    val effectOnBalance: Double // Positive means they owe you more, negative means you owe them more
)

enum class TransactionType { EXPENSE, PAYMENT }

data class DailyBalance(
    val date: LocalDate,
    val balance: Double
)

data class MemberDetailState(
    val isLoading: Boolean = false,
    val dailyBalances: List<DailyBalance> = emptyList(),
    val transactions: List<MemberTransaction> = emptyList(),
    val currentBalance: Double = 0.0,
    val error: String? = null
)

@Singleton
class MemberDetailRepository @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val _detailState = MutableStateFlow(MemberDetailState())
    val detailState = _detailState.asStateFlow()

    suspend fun fetchMemberDetails(groupId: String, currentUserId: String, otherUserId: String) {
        _detailState.value = _detailState.value.copy(isLoading = true, error = null)

        try {
            // Fetch all expenses and splits for the group (filtering locally is often simpler and reliable)
            val allExpenses = supabaseClient.postgrest[Tables.EXPENSES]
                .select { filter { eq("group_id", groupId) } }
                .decodeList<Expense>()

            val expenseIds = allExpenses.map { it.id }
            val allSplits = if (expenseIds.isNotEmpty()) {
                supabaseClient.postgrest[Tables.EXPENSE_SPLITS]
                    .select { filter { isIn("expense_id", expenseIds) } }
                    .decodeList<ExpenseSplit>()
            } else emptyList()

            // Fetch all payments for the group
            val allPayments = supabaseClient.postgrest[Tables.PAYMENTS]
                .select { filter { eq("group_id", groupId) } }
                .decodeList<Payment>()

            val transactions = mutableListOf<MemberTransaction>()

            // 1. Process Expenses
            for (expense in allExpenses) {
                if (expense.paidBy == currentUserId) {
                    val splitForOther = allSplits.find { it.expenseId == expense.id && it.userId == otherUserId }
                    if (splitForOther != null) {
                        transactions.add(
                            MemberTransaction(
                                id = expense.id,
                                date = expense.date,
                                description = expense.description,
                                amount = splitForOther.amount,
                                type = TransactionType.EXPENSE,
                                effectOnBalance = splitForOther.amount // They owe you
                            )
                        )
                    }
                } else if (expense.paidBy == otherUserId) {
                    val splitForMe = allSplits.find { it.expenseId == expense.id && it.userId == currentUserId }
                    if (splitForMe != null) {
                        transactions.add(
                            MemberTransaction(
                                id = expense.id,
                                date = expense.date,
                                description = expense.description,
                                amount = splitForMe.amount,
                                type = TransactionType.EXPENSE,
                                effectOnBalance = -splitForMe.amount // You owe them
                            )
                        )
                    }
                }
            }

            // 2. Process Payments
            for (payment in allPayments) {
                if (payment.fromUser == currentUserId && payment.toUser == otherUserId) {
                    transactions.add(
                        MemberTransaction(
                            id = payment.id,
                            date = payment.date,
                            description = "Pago a ${otherUserId.take(5)}...", // We don't have display names here, UI will format
                            amount = payment.amount,
                            type = TransactionType.PAYMENT,
                            effectOnBalance = payment.amount // Reduces how much you owe them (or increases how much they owe you)
                        )
                    )
                } else if (payment.fromUser == otherUserId && payment.toUser == currentUserId) {
                    transactions.add(
                        MemberTransaction(
                            id = payment.id,
                            date = payment.date,
                            description = "Pago de ${otherUserId.take(5)}...",
                            amount = payment.amount,
                            type = TransactionType.PAYMENT,
                            effectOnBalance = -payment.amount // Reduces how much they owe you
                        )
                    )
                }
            }

            // Sort chronological
            val sortedTransactions = transactions.sortedBy { it.date }

            // Calculate running balance and daily balances
            val today = LocalDate.now()
            val thirtyDaysAgo = today.minusDays(30)
            
            var runningBalance = 0.0
            val dailyMap = mutableMapOf<LocalDate, Double>()
            
            // Initialize map with last 30 days
            for (i in 0..30) {
                dailyMap[thirtyDaysAgo.plusDays(i.toLong())] = 0.0
            }

            for (t in sortedTransactions) {
                val date = LocalDate.parse(t.date.take(10), DateTimeFormatter.ISO_LOCAL_DATE)
                runningBalance += t.effectOnBalance
                
                // Keep track of balance at the end of each date
                if (!date.isBefore(thirtyDaysAgo)) {
                    dailyMap[date] = runningBalance
                }
            }

            // Fill gaps in daily map
            var lastKnownBalance = 0.0
            // We need to find the balance right before the 30-day window
            val beforeWindowTransactions = sortedTransactions.filter {
                LocalDate.parse(it.date.take(10), DateTimeFormatter.ISO_LOCAL_DATE).isBefore(thirtyDaysAgo)
            }
            lastKnownBalance = beforeWindowTransactions.sumOf { it.effectOnBalance }

            val dailyBalances = mutableListOf<DailyBalance>()
            var current = lastKnownBalance
            
            for (i in 0..30) {
                val date = thirtyDaysAgo.plusDays(i.toLong())
                if (dailyMap.containsKey(date) && dailyMap[date] != 0.0) {
                    // Update current to the balance of that day
                    // Wait, if there are multiple transactions on the same day, dailyMap only holds the last one or we overwritten it.
                    // Actually, the loop `dailyMap[date] = runningBalance` will end up with the balance after the LAST transaction of that day.
                    // But if no transactions happened that day, it's 0.0, which might be wrong if previous balance was non-zero.
                    // We must carry over the balance.
                }
            }
            
            // Better algorithm to carry over balance:
            current = lastKnownBalance
            for (i in 0..30) {
                val date = thirtyDaysAgo.plusDays(i.toLong())
                // Get all transactions for this date
                val dayTransactions = sortedTransactions.filter {
                    LocalDate.parse(it.date.take(10), DateTimeFormatter.ISO_LOCAL_DATE) == date
                }
                for (t in dayTransactions) {
                    current += t.effectOnBalance
                }
                dailyBalances.add(DailyBalance(date, current))
            }

            // Descending order for UI list
            val uiTransactions = sortedTransactions.sortedByDescending { it.date }

            _detailState.value = MemberDetailState(
                isLoading = false,
                dailyBalances = dailyBalances,
                transactions = uiTransactions,
                currentBalance = current
            )

        } catch (e: Exception) {
            _detailState.value = _detailState.value.copy(
                isLoading = false,
                error = e.message ?: "Unknown error"
            )
        }
    }
}
