package com.example.repartija.data.repository

import com.example.repartija.AppConstants.Tables
import com.example.repartija.data.model.Expense
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val _expenses = MutableStateFlow<DataResult<List<Expense>>>(DataResult.Loading)
    val expenses: StateFlow<DataResult<List<Expense>>> = _expenses.asStateFlow()

    suspend fun fetchExpenses(groupId: String): DataResult<List<Expense>> {
        _expenses.value = DataResult.Loading
        return try {
            val result = supabaseClient.postgrest[Tables.EXPENSES].select {
                filter { eq("group_id", groupId) }
            }.decodeList<Expense>()
            _expenses.value = DataResult.Success(result)
            DataResult.Success(result)
        } catch (e: Exception) {
            val error = DataResult.Error(e.message ?: "Unknown Error", e)
            _expenses.value = error
            error
        }
    }

    suspend fun addExpense(expense: Expense): DataResult<Expense> {
        return try {
            val result = supabaseClient.postgrest[Tables.EXPENSES].insert(expense) {
                select()
            }.decodeSingle<Expense>()
            fetchExpenses(expense.groupId)
            DataResult.Success(result)
        } catch (e: Exception) {
            DataResult.Error(e.message ?: "Unknown Error", e)
        }
    }
    suspend fun deleteExpense(expenseId: String, groupId: String): DataResult<Unit> {
        return try {
            supabaseClient.postgrest[Tables.EXPENSES].delete {
                filter { eq("id", expenseId) }
            }
            fetchExpenses(groupId)
            DataResult.Success(Unit)
        } catch (e: Exception) {
            DataResult.Error(e.message ?: "Unknown Error", e)
        }
    }

    suspend fun updateExpense(expense: Expense): DataResult<Expense> {
        return try {
            val result = supabaseClient.postgrest[Tables.EXPENSES].update(expense) {
                filter { eq("id", expense.id) }
                select()
            }.decodeSingle<Expense>()
            fetchExpenses(expense.groupId)
            DataResult.Success(result)
        } catch (e: Exception) {
            DataResult.Error(e.message ?: "Unknown Error", e)
        }
    }
}
