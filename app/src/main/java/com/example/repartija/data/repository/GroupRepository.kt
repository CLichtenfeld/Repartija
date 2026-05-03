package com.example.repartija.data.repository

import com.example.repartija.AppConstants.Tables
import com.example.repartija.data.model.Group
import com.example.repartija.data.model.GroupMember
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepository @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val _groups = MutableStateFlow<DataResult<List<Group>>>(DataResult.Loading)
    val groups: StateFlow<DataResult<List<Group>>> = _groups.asStateFlow()

    /**
     * Fetches only the groups where the given userId is a member.
     */
    suspend fun fetchGroupsForUser(userId: String): DataResult<List<Group>> {
        _groups.value = DataResult.Loading
        return try {
            // First get the group_ids this user belongs to
            val memberships = supabaseClient.postgrest[Tables.GROUP_MEMBERS]
                .select { filter { eq("user_id", userId) } }
                .decodeList<GroupMember>()
            val groupIds = memberships.map { it.groupId }

            if (groupIds.isEmpty()) {
                _groups.value = DataResult.Success(emptyList())
                return DataResult.Success(emptyList())
            }

            val result = supabaseClient.postgrest[Tables.GROUPS]
                .select { filter { isIn("id", groupIds) } }
                .decodeList<Group>()
            _groups.value = DataResult.Success(result)
            DataResult.Success(result)
        } catch (e: Exception) {
            val error = DataResult.Error(e.message ?: "Unknown Error", e)
            _groups.value = error
            error
        }
    }

    suspend fun createGroup(group: Group): DataResult<Group> {
        return try {
            val result = supabaseClient.postgrest[Tables.GROUPS].insert(group) {
                select()
            }.decodeSingle<Group>()
            DataResult.Success(result)
        } catch (e: Exception) {
            DataResult.Error(e.message ?: "Unknown Error", e)
        }
    }
}
