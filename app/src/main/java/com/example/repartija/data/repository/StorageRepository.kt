package com.example.repartija.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageRepository @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    suspend fun uploadAvatar(userId: String, bytes: ByteArray): DataResult<String> {
        return try {
            val bucket = supabaseClient.storage["avatars"]
            val fileName = "$userId/avatar_${System.currentTimeMillis()}.jpg"
            
            // Upload to the avatars bucket
            bucket.upload(fileName, bytes) {
                upsert = true
            }
            
            // Get public URL
            val url = bucket.publicUrl(fileName)
            DataResult.Success(url)
        } catch (e: Exception) {
            DataResult.Error(e.message ?: "Error uploading image", e)
        }
    }
}
