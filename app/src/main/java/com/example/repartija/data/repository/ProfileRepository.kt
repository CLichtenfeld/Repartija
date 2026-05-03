package com.example.repartija.data.repository

import com.example.repartija.AppConstants.Tables
import com.example.repartija.data.model.Profile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val _profiles = MutableStateFlow<DataResult<List<Profile>>>(DataResult.Loading)
    val profiles: StateFlow<DataResult<List<Profile>>> = _profiles.asStateFlow()

    suspend fun fetchProfiles(): DataResult<List<Profile>> {
        _profiles.value = DataResult.Loading
        return try {
            val result = supabaseClient.postgrest[Tables.PROFILES]
                .select().decodeList<Profile>()
            _profiles.value = DataResult.Success(result)
            DataResult.Success(result)
        } catch (e: Exception) {
            val error = DataResult.Error(e.message ?: "Unknown Error", e)
            _profiles.value = error
            error
        }
    }

    suspend fun getProfile(id: String): DataResult<Profile> {
        return try {
            val result = supabaseClient.postgrest[Tables.PROFILES]
                .select { filter { eq("id", id) } }
                .decodeSingle<Profile>()
            DataResult.Success(result)
        } catch (e: Exception) {
            DataResult.Error(e.message ?: "Unknown Error", e)
        }
    }

    suspend fun searchByEmail(email: String): DataResult<Profile> {
        return try {
            val result = supabaseClient.postgrest[Tables.PROFILES]
                .select { filter { eq("email", email) } }
                .decodeSingle<Profile>()
            DataResult.Success(result)
        } catch (e: Exception) {
            DataResult.Error("No se encontró un usuario con ese email", e)
        }
    }
}
