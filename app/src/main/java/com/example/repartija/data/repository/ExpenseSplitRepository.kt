package com.example.repartija.data.repository

import com.example.repartija.AppConstants.Tables
import com.example.repartija.data.model.ExpenseSplit
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseSplitRepository @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val _splits = MutableStateFlow<DataResult<List<ExpenseSplit>>>(DataResult.Loading)
    val splits: StateFlow<DataResult<List<ExpenseSplit>>> = _splits.asStateFlow()

    suspend fun fetchSplitsForExpense(expenseId: String): DataResult<List<ExpenseSplit>> {
        _splits.value = DataResult.Loading
        return try {
            val result = supabaseClient.postgrest[Tables.EXPENSE_SPLITS].select {
                filter { eq("expense_id", expenseId) }
            }.decodeList<ExpenseSplit>()
            _splits.value = DataResult.Success(result)
            DataResult.Success(result)
        } catch (e: Exception) {
            val error = DataResult.Error(e.message ?: "Unknown Error", e)
            _splits.value = error
            error
        }
    }

    suspend fun addSplits(splits: List<ExpenseSplit>): DataResult<List<ExpenseSplit>> {
        return try {
            val result = supabaseClient.postgrest[Tables.EXPENSE_SPLITS].insert(splits) {
                select()
            }.decodeList<ExpenseSplit>()
            if (splits.isNotEmpty()) {
                fetchSplitsForExpense(splits.first().expenseId)
            }
            DataResult.Success(result)
        } catch (e: Exception) {
            DataResult.Error(e.message ?: "Unknown Error", e)
        }
    }
}
