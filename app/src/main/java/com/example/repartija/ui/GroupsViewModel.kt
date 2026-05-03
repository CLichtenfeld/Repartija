package com.example.repartija.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.repartija.data.model.*
import com.example.repartija.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val sessionRepository: SessionRepository,
    private val expenseRepository: ExpenseRepository,
    private val expenseSplitRepository: ExpenseSplitRepository,
    private val paymentRepository: PaymentRepository,
    private val balanceRepository: BalanceRepository
) : ViewModel() {

    val allGroups: StateFlow<DataResult<List<Group>>> = groupRepository.groups

    init {
        refreshGroups()
    }

    fun refreshGroups() {
        viewModelScope.launch {
            sessionRepository.currentUser.value?.id?.let { uid ->
                groupRepository.fetchGroupsForUser(uid)
            }
        }
    }

    fun addGroup(name: String) {
        val userId = sessionRepository.currentUser.value?.id ?: return
        viewModelScope.launch {
            val res = groupRepository.createGroup(Group(name = name, createdBy = userId))
            if (res is DataResult.Success) {
                groupMemberRepository.addMember(GroupMember(groupId = res.data.id, userId = userId))
            }
        }
    }

    fun renameGroup(groupId: String, newName: String) {
        viewModelScope.launch {
            groupRepository.updateGroup(groupId, newName)
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            groupRepository.deleteGroup(groupId)
        }
    }

    suspend fun areAllBalancesZero(groupId: String): Boolean {
        val expRes = expenseRepository.fetchExpenses(groupId)
        if (expRes !is DataResult.Success) return false

        val allSplits = mutableListOf<ExpenseSplit>()
        for (exp in expRes.data) {
            val sr = expenseSplitRepository.fetchSplitsForExpense(exp.id)
            if (sr is DataResult.Success) allSplits.addAll(sr.data)
        }

        val payRes = paymentRepository.fetchPayments(groupId)
        if (payRes !is DataResult.Success) return false

        val debts = balanceRepository.calculateSimplifiedDebts(expRes.data, allSplits, payRes.data)
        return debts.isEmpty()
    }
}
