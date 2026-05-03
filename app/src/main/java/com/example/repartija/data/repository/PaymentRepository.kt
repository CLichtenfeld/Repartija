package com.example.repartija.data.repository

import com.example.repartija.AppConstants.Tables
import com.example.repartija.data.model.Payment
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentRepository @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val _payments = MutableStateFlow<DataResult<List<Payment>>>(DataResult.Loading)
    val payments: StateFlow<DataResult<List<Payment>>> = _payments.asStateFlow()

    suspend fun fetchPayments(groupId: String): DataResult<List<Payment>> {
        _payments.value = DataResult.Loading
        return try {
            val result = supabaseClient.postgrest[Tables.PAYMENTS].select {
                filter { eq("group_id", groupId) }
            }.decodeList<Payment>()
            _payments.value = DataResult.Success(result)
            DataResult.Success(result)
        } catch (e: Exception) {
            val error = DataResult.Error(e.message ?: "Unknown Error", e)
            _payments.value = error
            error
        }
    }

    suspend fun addPayment(payment: Payment): DataResult<Payment> {
        return try {
            val result = supabaseClient.postgrest[Tables.PAYMENTS].insert(payment) {
                select()
            }.decodeSingle<Payment>()
            fetchPayments(payment.groupId)
            DataResult.Success(result)
        } catch (e: Exception) {
            DataResult.Error(e.message ?: "Unknown Error", e)
        }
    }
}
