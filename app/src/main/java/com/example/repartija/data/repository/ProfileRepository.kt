package com.example.repartija.data.repository

import android.util.Log
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
            Log.d("ProfileRepository", "Fetched ${result.size} profiles")
            result.forEach { 
                Log.v("ProfileRepository", "Profile: id=${it.id}, email=${it.email}, name=${it.displayName}")
            }
            _profiles.value = DataResult.Success(result)
            DataResult.Success(result)
        } catch (e: Exception) {
            Log.e("ProfileRepository", "Error fetching profiles", e)
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
        Log.d("ProfileRepository", "Searching for profile with email: $email")
        return try {
            val result = supabaseClient.postgrest[Tables.PROFILES]
                .select {
                    filter {
                        ilike("email", email)
                    }
                }
                .decodeList<Profile>()
            
            Log.d("ProfileRepository", "Search result for $email: ${result.size} matches")
            if (result.isNotEmpty()) {
                val profile = result.first()
                Log.d("ProfileRepository", "Found profile: ${profile.id} - ${profile.displayName}")
                DataResult.Success(profile)
            } else {
                Log.w("ProfileRepository", "No profile found for email: $email")
                DataResult.Error("No se encontró un usuario con ese email")
            }
        } catch (e: Exception) {
            Log.e("ProfileRepository", "Error searching for email $email", e)
            DataResult.Error("Error al buscar usuario: ${e.message}", e)
        }
    }

    suspend fun updateProfile(profile: Profile): DataResult<Profile> {
        return try {
            val result = supabaseClient.postgrest[Tables.PROFILES].update(profile) {
                filter { eq("id", profile.id) }
                select()
            }.decodeSingle<Profile>()
            DataResult.Success(result)
        } catch (e: Exception) {
            DataResult.Error(e.message ?: "Unknown Error", e)
        }
    }
}
