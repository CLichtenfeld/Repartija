package com.example.repartija.data.repository

import com.example.repartija.AppConstants.Tables
import com.example.repartija.data.model.GroupMember
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupMemberRepository @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val _members = MutableStateFlow<DataResult<List<GroupMember>>>(DataResult.Loading)
    val members: StateFlow<DataResult<List<GroupMember>>> = _members.asStateFlow()

    suspend fun fetchMembers(groupId: String): DataResult<List<GroupMember>> {
        _members.value = DataResult.Loading
        return try {
            val result = supabaseClient.postgrest[Tables.GROUP_MEMBERS].select {
                filter { eq("group_id", groupId) }
            }.decodeList<GroupMember>()
            _members.value = DataResult.Success(result)
            DataResult.Success(result)
        } catch (e: Exception) {
            val error = DataResult.Error(e.message ?: "Unknown Error", e)
            _members.value = error
            error
        }
    }

    suspend fun addMember(member: GroupMember): DataResult<GroupMember> {
        return try {
            val result = supabaseClient.postgrest[Tables.GROUP_MEMBERS].insert(member) {
                select()
            }.decodeSingle<GroupMember>()
            fetchMembers(member.groupId)
            DataResult.Success(result)
        } catch (e: Exception) {
            DataResult.Error(e.message ?: "Unknown Error", e)
        }
    }
}
