package com.example.repartija.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    suspend fun sendNotification(
        groupId: String,
        transactionId: String,
        transactionType: String,
        changedBy: String,
        affectedUserIds: List<String>
    ) {
        try {
            val jsonBody = buildJsonObject {
                put("groupId", groupId)
                put("transactionId", transactionId)
                put("transactionType", transactionType)
                put("changedBy", changedBy)
                putJsonArray("affectedUserIds") {
                    affectedUserIds.forEach { add(it) }
                }
            }

            supabaseClient.functions.invoke("notify-transaction", jsonBody)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
