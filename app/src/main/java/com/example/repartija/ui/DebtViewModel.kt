package com.example.repartija.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.repartija.data.model.*
import com.example.repartija.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class DebtViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val profileRepository: ProfileRepository,
    private val expenseRepository: ExpenseRepository,
    private val expenseSplitRepository: ExpenseSplitRepository,
    private val paymentRepository: PaymentRepository,
    private val balanceRepository: BalanceRepository,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _selectedGroupId = MutableStateFlow<String?>(null)
    val selectedGroupId: StateFlow<String?> = _selectedGroupId.asStateFlow()

    /** The current user's Supabase UUID, set from the auth session. */
    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    val allGroups: StateFlow<DataResult<List<Group>>> = groupRepository.groups

    init {
        // Track auth session → set userId → fetch groups
        viewModelScope.launch {
            sessionRepository.currentUser.collect { user ->
                val uid = user?.id
                _currentUserId.value = uid
                if (uid != null) {
                    groupRepository.fetchGroupsForUser(uid)
                }
            }
        }
    }

    // ── Members for the selected group ──────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentMembers: StateFlow<DataResult<List<Profile>>> = _selectedGroupId
        .flatMapLatest { groupId ->
            if (groupId == null) return@flatMapLatest flowOf<DataResult<List<Profile>>>(DataResult.Success(emptyList()))

            flow {
                emit(DataResult.Loading)
                val membersRes = groupMemberRepository.fetchMembers(groupId)
                if (membersRes is DataResult.Success) {
                    val profiles = mutableListOf<Profile>()
                    for (member in membersRes.data) {
                        val profileRes = profileRepository.getProfile(member.userId)
                        if (profileRes is DataResult.Success) {
                            profiles.add(profileRes.data)
                        }
                    }
                    emit(DataResult.Success(profiles))
                } else if (membersRes is DataResult.Error) {
                    emit(DataResult.Error(membersRes.message))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DataResult.Loading)

    // ── Expenses ────────────────────────────────────────────────────

    /** Trigger to force re-fetch of expenses/payments inside a group */
    private val _refreshTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentExpenses: StateFlow<DataResult<List<Expense>>> = combine(
        _selectedGroupId, _refreshTrigger
    ) { id, _ -> id }
        .flatMapLatest { id ->
            if (id != null) {
                flow { emit(expenseRepository.fetchExpenses(id)) }
            } else flowOf<DataResult<List<Expense>>>(DataResult.Success(emptyList()))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DataResult.Loading)

    // ── Expense splits ──────────────────────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentSplits: StateFlow<DataResult<List<ExpenseSplit>>> = currentExpenses
        .flatMapLatest { expensesRes ->
            if (expensesRes is DataResult.Success) {
                flow {
                    val allSplits = mutableListOf<ExpenseSplit>()
                    for (exp in expensesRes.data) {
                        val splitsRes = expenseSplitRepository.fetchSplitsForExpense(exp.id)
                        if (splitsRes is DataResult.Success) {
                            allSplits.addAll(splitsRes.data)
                        }
                    }
                    emit(DataResult.Success(allSplits))
                }
            } else {
                flowOf<DataResult<List<ExpenseSplit>>>(DataResult.Success(emptyList()))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DataResult.Loading)

    // ── Payments ────────────────────────────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentPayments: StateFlow<DataResult<List<Payment>>> = combine(
        _selectedGroupId, _refreshTrigger
    ) { id, _ -> id }
        .flatMapLatest { id ->
            if (id != null) {
                flow { emit(paymentRepository.fetchPayments(id)) }
            } else flowOf<DataResult<List<Payment>>>(DataResult.Success(emptyList()))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DataResult.Loading)

    // ── Simplified debts (calculated in Repository) ─────────────────

    val currentDebts: StateFlow<List<PairBalance>> = combine(
        currentExpenses, currentSplits, currentPayments
    ) { expRes, splitRes, payRes ->
        if (expRes is DataResult.Success && splitRes is DataResult.Success && payRes is DataResult.Success) {
            balanceRepository.calculateSimplifiedDebts(expRes.data, splitRes.data, payRes.data)
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Actions ─────────────────────────────────────────────────────

    fun selectGroup(groupId: String?) {
        _selectedGroupId.value = groupId
    }

    fun addGroup(name: String) {
        val userId = _currentUserId.value ?: return
        viewModelScope.launch {
            val res = groupRepository.createGroup(Group(name = name, createdBy = userId))
            if (res is DataResult.Success) {
                groupMemberRepository.addMember(GroupMember(groupId = res.data.id, userId = userId))
                groupRepository.fetchGroupsForUser(userId) // Refresh groups list
            }
        }
    }

    fun makePayment(toUserId: String, amount: Double) {
        val groupId = _selectedGroupId.value ?: return
        val fromUserId = _currentUserId.value ?: return
        viewModelScope.launch {
            paymentRepository.addPayment(
                Payment(
                    groupId = groupId,
                    fromUser = fromUserId,
                    toUser = toUserId,
                    amount = amount,
                    date = LocalDate.now().toString()
                )
            )
            _refreshTrigger.value++
        }
    }

    fun addNewExpense(description: String, amount: Double, paidBy: String, shares: Map<String, Double>) {
        val groupId = _selectedGroupId.value ?: return
        viewModelScope.launch {
            val expRes = expenseRepository.addExpense(
                Expense(
                    groupId = groupId,
                    paidBy = paidBy,
                    amount = amount,
                    description = description,
                    date = LocalDate.now().toString()
                )
            )
            if (expRes is DataResult.Success) {
                val splits = shares.map { (userId, shareAmount) ->
                    ExpenseSplit(
                        expenseId = expRes.data.id,
                        userId = userId,
                        amount = shareAmount
                    )
                }
                expenseSplitRepository.addSplits(splits)
            }
            _refreshTrigger.value++
        }
    }
}
