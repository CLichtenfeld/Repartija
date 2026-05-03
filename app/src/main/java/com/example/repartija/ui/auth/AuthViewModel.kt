package com.example.repartija.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.repartija.data.repository.DataResult
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

sealed interface AuthState {
    data object Idle : AuthState
    data object Loading : AuthState
    data object Success : AuthState
    data class Error(val message: String) : AuthState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val supabaseClient: SupabaseClient
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun clearState() {
        _authState.value = AuthState.Idle
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = runAuthCatching {
                supabaseClient.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
            }
            _authState.value = when (result) {
                is DataResult.Success -> AuthState.Success
                is DataResult.Error -> AuthState.Error(result.message)
                is DataResult.Loading -> AuthState.Loading
            }
        }
    }

    fun register(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = runAuthCatching {
                supabaseClient.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                    data = buildJsonObject {
                        put("display_name", displayName)
                    }
                }
            }
            _authState.value = when (result) {
                is DataResult.Success -> AuthState.Success
                is DataResult.Error -> AuthState.Error(result.message)
                is DataResult.Loading -> AuthState.Loading
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            runAuthCatching { supabaseClient.auth.signOut() }
        }
    }

    private suspend fun <T> runAuthCatching(block: suspend () -> T): DataResult<T> {
        return try {
            DataResult.Success(block())
        } catch (e: Exception) {
            DataResult.Error(e.localizedMessage ?: "Error desconocido", e)
        }
    }
}
