package com.example.repartija.data.repository

import com.example.repartija.AppConstants.Tables
import com.example.repartija.data.model.GroupInvite
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupInviteRepository @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val _invites = MutableStateFlow<DataResult<List<GroupInvite>>>(DataResult.Loading)
    val invites: StateFlow<DataResult<List<GroupInvite>>> = _invites.asStateFlow()

    suspend fun fetchInvites(groupId: String): DataResult<List<GroupInvite>> {
        _invites.value = DataResult.Loading
        return try {
            val result = supabaseClient.postgrest[Tables.GROUP_INVITES].select {
                filter { eq("group_id", groupId) }
            }.decodeList<GroupInvite>()
            _invites.value = DataResult.Success(result)
            DataResult.Success(result)
        } catch (e: Exception) {
            val error = DataResult.Error(e.message ?: "Unknown Error", e)
            _invites.value = error
            error
        }
    }

    suspend fun createInvite(invite: GroupInvite): DataResult<GroupInvite> {
        return try {
            val result = supabaseClient.postgrest[Tables.GROUP_INVITES].insert(invite) {
                select()
            }.decodeSingle<GroupInvite>()
            fetchInvites(invite.groupId)
            DataResult.Success(result)
        } catch (e: Exception) {
            DataResult.Error(e.message ?: "Unknown Error", e)
        }
    }

    suspend fun getInviteByToken(token: String): DataResult<GroupInvite> {
        return try {
            val result = supabaseClient.postgrest[Tables.GROUP_INVITES].select {
                filter { eq("token", token) }
            }.decodeSingle<GroupInvite>()
            DataResult.Success(result)
        } catch (e: Exception) {
            DataResult.Error("Invite not found or invalid token", e)
        }
    }

    suspend fun markInviteAsUsed(inviteId: String): DataResult<Unit> {
        return try {
            supabaseClient.postgrest[Tables.GROUP_INVITES].update({
                set("used", true)
            }) {
                filter { eq("id", inviteId) }
            }
            DataResult.Success(Unit)
        } catch (e: Exception) {
            DataResult.Error(e.message ?: "Unknown Error", e)
        }
    }

    suspend fun deleteInvite(inviteId: String, groupId: String): DataResult<Unit> {
        return try {
            supabaseClient.postgrest[Tables.GROUP_INVITES].delete {
                filter { eq("id", inviteId) }
            }
            fetchInvites(groupId)
            DataResult.Success(Unit)
        } catch (e: Exception) {
            DataResult.Error(e.message ?: "Unknown Error", e)
        }
    }
}
