package com.example.repartija.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.repartija.data.repository.MemberDetailRepository
import com.example.repartija.data.repository.MemberDetailState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemberDetailViewModel @Inject constructor(
    private val repository: MemberDetailRepository
) : ViewModel() {

    val state: StateFlow<MemberDetailState> = repository.detailState

    fun loadDetails(groupId: String, currentUserId: String, otherUserId: String) {
        viewModelScope.launch {
            repository.fetchMemberDetails(groupId, currentUserId, otherUserId)
        }
    }
}
