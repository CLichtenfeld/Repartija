package com.example.repartija.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.repartija.data.*
import com.example.repartija.domain.SettlementManager
import com.example.repartija.domain.SettlementSuggestion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.min

class DebtViewModel(private val repository: DebtRepository) : ViewModel() {

    private val _selectedGroupId = MutableStateFlow<Int?>(null)
    val selectedGroupId: StateFlow<Int?> = _selectedGroupId.asStateFlow()

    private val _currentMemberId = MutableStateFlow<Int?>(null)
    val currentMemberId: StateFlow<Int?> = _currentMemberId.asStateFlow()

    val allGroups: StateFlow<List<Group>> = repository.getAllGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentMembers: StateFlow<List<Member>> = _selectedGroupId
        .flatMapLatest { id -> id?.let { repository.getMembersByGroup(it) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentDebts: StateFlow<List<Debt>> = _selectedGroupId
        .flatMapLatest { id -> id?.let { repository.getDebtsByGroup(it) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentPayments: StateFlow<List<Payment>> = _selectedGroupId
        .flatMapLatest { id -> id?.let { repository.getPaymentsByGroup(it) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _globalTna = MutableStateFlow(30.0)
    val globalTna: StateFlow<Double> = _globalTna.asStateFlow()

    val settlementSuggestions: StateFlow<List<SettlementSuggestion>> = 
        combine(currentDebts, currentMembers) { debts, members ->
            SettlementManager.calculateSuggestions(debts, members)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectGroup(groupId: Int?) {
        _selectedGroupId.value = groupId
        // Reset current member when group changes if needed, or keep it if it belongs to the group
    }

    fun setCurrentMember(memberId: Int?) {
        _currentMemberId.value = memberId
    }

    fun addGroup(name: String) {
        viewModelScope.launch {
            repository.addGroup(Group(name = name))
        }
    }

    fun updateGlobalTna(newTna: Double) {
        _globalTna.value = newTna
    }

    fun updateAllInterests() {
        viewModelScope.launch {
            val debts = currentDebts.value
            val today = LocalDate.now()
            debts.forEach { debt ->
                repository.updateDebtInterest(debt.id, today)
            }
        }
    }

    fun makePayment(debtId: Int, amount: Double) {
        viewModelScope.launch {
            repository.registerPayment(debtId, amount, LocalDate.now())
        }
    }

    fun deletePayment(payment: Payment) {
        viewModelScope.launch {
            repository.deletePayment(payment)
        }
    }

    fun deleteExpense(debt: Debt) {
        viewModelScope.launch {
            repository.deleteDebt(debt)
        }
    }

    fun updateExpense(debt: Debt, newDescription: String, newAmount: Double) {
        viewModelScope.launch {
            val updatedDebt = debt.copy(
                description = newDescription,
                originalAmount = newAmount,
                remainingBalance = newAmount
            )
            repository.updateDebt(updatedDebt)
        }
    }

    fun executeSettlement(suggestion: SettlementSuggestion) {
        val groupId = _selectedGroupId.value ?: return
        viewModelScope.launch {
            repository.executeSettlement(
                suggestion.fromMember.id,
                suggestion.toMember.id,
                suggestion.amount,
                LocalDate.now(),
                groupId
            )
        }
    }

    fun updateMember(member: Member, newName: String) {
        viewModelScope.launch {
            repository.updateMember(member.copy(name = newName))
        }
    }

    fun deleteMember(member: Member) {
        viewModelScope.launch {
            repository.deleteMember(member)
        }
    }
    
    private data class BalanceNode(val id: Int, var amount: Double)

    fun addNewExpense(description: String, paidAmounts: Map<Int, Double>, shares: Map<Int, Double>) {
        val groupId = _selectedGroupId.value ?: return
        val tna = _globalTna.value
        val today = LocalDate.now()

        val netBalances = mutableMapOf<Int, Double>()
        val allMemberIds = (paidAmounts.keys + shares.keys).toSet()
        
        allMemberIds.forEach { id ->
            val paid = paidAmounts[id] ?: 0.0
            val share = shares[id] ?: 0.0
            netBalances[id] = paid - share
        }

        val creditors = netBalances.filter { it.value > 0.01 }
            .map { BalanceNode(it.key, it.value) }
            .sortedByDescending { it.amount }
            .toMutableList()
            
        val debtors = netBalances.filter { it.value < -0.01 }
            .map { BalanceNode(it.key, abs(it.value)) }
            .sortedByDescending { it.amount }
            .toMutableList()

        viewModelScope.launch {
            var cIdx = 0
            var dIdx = 0
            
            while (cIdx < creditors.size && dIdx < debtors.size) {
                val creditor = creditors[cIdx]
                val debtor = debtors[dIdx]
                val settlementAmount = min(creditor.amount, debtor.amount)
                
                if (settlementAmount > 0.01) {
                    repository.addDebt(Debt(
                        groupId = groupId,
                        description = description,
                        fromMemberId = debtor.id,
                        toMemberId = creditor.id,
                        originalAmount = settlementAmount,
                        currency = "ARS",
                        remainingBalance = settlementAmount,
                        annualRate = tna,
                        startDate = today,
                        lastInterestDate = today
                    ))
                }
                
                creditor.amount -= settlementAmount
                debtor.amount -= settlementAmount
                if (creditor.amount < 0.01) cIdx++
                if (debtor.amount < 0.01) dIdx++
            }
        }
    }

    fun addMember(name: String) {
        val groupId = _selectedGroupId.value ?: return
        viewModelScope.launch {
            repository.addMember(Member(groupId = groupId, name = name))
        }
    }
}
