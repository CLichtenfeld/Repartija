package com.example.repartija.data.repository

import com.example.repartija.AppConstants.Tables
import com.example.repartija.data.model.ExpensePayer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpensePayerRepository @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val _payers = MutableStateFlow<DataResult<List<ExpensePayer>>>(DataResult.Loading)
    val payers: StateFlow<DataResult<List<ExpensePayer>>> = _payers.asStateFlow()

    suspend fun fetchPayersForExpense(expenseId: String): DataResult<List<ExpensePayer>> {
        _payers.value = DataResult.Loading
        return try {
            val result = supabaseClient.postgrest[Tables.EXPENSE_PAYERS].select {
                filter { eq("expense_id", expenseId) }
            }.decodeList<ExpensePayer>()
            _payers.value = DataResult.Success(result)
            DataResult.Success(result)
        } catch (e: Exception) {
            DataResult.Error(e.message ?: "Unknown Error", e)
        }
    }

    suspend fun addPayers(payers: List<ExpensePayer>): DataResult<List<ExpensePayer>> {
        return try {
            val result = supabaseClient.postgrest[Tables.EXPENSE_PAYERS].insert(payers) {
                select()
            }.decodeList<ExpensePayer>()
            DataResult.Success(result)
        } catch (e: Exception) {
            DataResult.Error(e.message ?: "Unknown Error", e)
        }
    }

    suspend fun deletePayersForExpense(expenseId: String): DataResult<Unit> {
        return try {
            supabaseClient.postgrest[Tables.EXPENSE_PAYERS].delete {
                filter { eq("id", expenseId) }
            }
            DataResult.Success(Unit)
        } catch (e: Exception) {
            DataResult.Error(e.message ?: "Unknown Error", e)
        }
    }
}
