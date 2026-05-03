package com.example.repartija.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    val currentUser: StateFlow<UserInfo?> = supabaseClient.auth.sessionStatus
        .map { status ->
            when (status) {
                is SessionStatus.Authenticated -> status.session.user
                else -> null
            }
        }
        .stateIn(
            scope = CoroutineScope(Dispatchers.IO),
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    suspend fun login(email: String, pass: String) {
        supabaseClient.auth.signInWith(Email) {
            this.email = email
            password = pass
        }
    }

    suspend fun register(email: String, pass: String, displayName: String) {
        supabaseClient.auth.signUpWith(Email) {
            this.email = email
            password = pass
            data = buildJsonObject {
                put("display_name", displayName)
            }
        }
    }

    suspend fun logout() {
        supabaseClient.auth.signOut()
    }
}
