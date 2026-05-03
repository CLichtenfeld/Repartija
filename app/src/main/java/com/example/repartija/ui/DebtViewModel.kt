package com.example.repartija.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.repartija.AppConstants
import com.example.repartija.AppConstants.Tables
import com.example.repartija.data.model.*
import com.example.repartija.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
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
    private val sessionRepository: SessionRepository,
    private val realtimeManager: RealtimeManager,
    private val groupInviteRepository: GroupInviteRepository
) : ViewModel() {

    private val _selectedGroupId = MutableStateFlow<String?>(null)
    val selectedGroupId: StateFlow<String?> = _selectedGroupId.asStateFlow()

    /** The current user's Supabase UUID, set from the auth session. */
    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    /** Realtime sync indicator */
    val isSyncing: StateFlow<Boolean> = realtimeManager.isSyncing

    val selectedGroup: StateFlow<DataResult<Group?>> = combine(
        groupRepository.groups, _selectedGroupId
    ) { groupsRes, selectedId ->
        if (groupsRes is DataResult.Success) {
            DataResult.Success(groupsRes.data.find { it.id == selectedId })
        } else {
            DataResult.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DataResult.Loading)

    /** Trigger to force re-fetch of expenses/payments/members inside a group */
    private val _refreshTrigger = MutableStateFlow(0)

    private val _pendingJoinToken = MutableStateFlow<String?>(null)
    val pendingJoinToken: StateFlow<String?> = _pendingJoinToken.asStateFlow()

    init {
        observeAuthSession()
        observeSelectedGroupChanges()
        observeRealtimeChanges()
    }

    private fun observeAuthSession() {
        viewModelScope.launch {
            sessionRepository.currentUser.collect { user ->
                val uid = user?.id
                _currentUserId.value = uid
                if (uid != null) {
                    groupRepository.fetchGroupsForUser(uid)
                    handlePendingJoinToken()
                }
            }
        }
    }

    private fun handlePendingJoinToken() {
        val token = _pendingJoinToken.value
        if (token != null) {
            processPendingToken(token)
            _pendingJoinToken.value = null
        }
    }

    private fun observeSelectedGroupChanges() {
        viewModelScope.launch {
            _selectedGroupId.collect { groupId ->
                if (groupId != null) {
                    realtimeManager.subscribeToGroup(groupId)
                } else {
                    realtimeManager.unsubscribe()
                }
            }
        }
    }

    private fun observeRealtimeChanges() {
        viewModelScope.launch {
            realtimeManager.tableChanged.collect { table ->
                val groupId = _selectedGroupId.value ?: return@collect
                when (table) {
                    Tables.EXPENSES, Tables.EXPENSE_SPLITS, Tables.PAYMENTS -> {
                        _refreshTrigger.value++
                    }
                    Tables.GROUP_MEMBERS -> {
                        _memberRefreshTrigger.value++
                        _currentUserId.value?.let { groupRepository.fetchGroupsForUser(it) }
                    }
                }
                realtimeManager.setSyncing(false)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeManager.unsubscribe()
    }

    // ── Members for the selected group ──────────────────────────────

    private val _memberRefreshTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentMembers: StateFlow<DataResult<List<Profile>>> = combine(
        _selectedGroupId, _memberRefreshTrigger
    ) { id, _ -> id }
        .flatMapLatest { groupId ->
            if (groupId == null) return@flatMapLatest flowOf<DataResult<List<Profile>>>(DataResult.Success(emptyList()))

            flow {
                emit(DataResult.Loading)
                emit(fetchMemberProfiles(groupId))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DataResult.Loading)

    private suspend fun fetchMemberProfiles(groupId: String): DataResult<List<Profile>> {
        val membersRes = groupMemberRepository.fetchMembers(groupId)
        return if (membersRes is DataResult.Success) {
            val profiles = membersRes.data.mapNotNull { member ->
                (profileRepository.getProfile(member.userId) as? DataResult.Success)?.data
            }
            DataResult.Success(profiles)
        } else {
            val errorMsg = (membersRes as? DataResult.Error)?.message ?: "Error fetching members"
            DataResult.Error(errorMsg)
        }
    }

    // ── Invites for the selected group ──────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentInvites: StateFlow<DataResult<List<GroupInvite>>> = combine(
        _selectedGroupId, _memberRefreshTrigger
    ) { id, _ -> id }
        .flatMapLatest { groupId ->
            if (groupId == null) flowOf(DataResult.Success(emptyList()))
            else flow { emit(groupInviteRepository.fetchInvites(groupId)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DataResult.Loading)

    // ── Expenses ────────────────────────────────────────────────────

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

    private val _actionResult = MutableStateFlow<String?>(null)
    val actionResult: StateFlow<String?> = _actionResult.asStateFlow()

    fun clearActionResult() { _actionResult.value = null }

    fun selectGroup(groupId: String?) {
        _selectedGroupId.value = groupId
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

    // ── Member management ───────────────────────────────────────────

    private val _searchResult = MutableStateFlow<DataResult<Profile>?>(null)
    val searchResult: StateFlow<DataResult<Profile>?> = _searchResult.asStateFlow()

    fun clearSearchResult() { _searchResult.value = null }

    fun searchUserByEmail(email: String) {
        viewModelScope.launch {
            _searchResult.value = DataResult.Loading
            _searchResult.value = profileRepository.searchByEmail(email)
        }
    }

    fun addMemberToCurrentGroup(userId: String) {
        val groupId = _selectedGroupId.value ?: return
        viewModelScope.launch {
            val res = groupMemberRepository.addMember(GroupMember(groupId = groupId, userId = userId))
            if (res is DataResult.Error) {
                _actionResult.value = res.message
            }
            _memberRefreshTrigger.value++
            _searchResult.value = null
        }
    }

    /**
     * Check if a specific member has a zero balance with everyone else in the group.
     */
    fun memberHasZeroBalance(userId: String): Boolean {
        val debts = currentDebts.value
        return debts.none { it.fromUserId == userId || it.toUserId == userId }
    }

    fun removeMemberFromCurrentGroup(userId: String) {
        val groupId = _selectedGroupId.value ?: return
        viewModelScope.launch {
            val res = groupMemberRepository.removeMember(groupId, userId)
            if (res is DataResult.Error) {
                _actionResult.value = res.message
            }
            _memberRefreshTrigger.value++
        }
    }

    // ── Invitations ─────────────────────────────────────────────────

    fun setPendingJoinToken(token: String) {
        _pendingJoinToken.value = token
    }

    /**
     * Generates a unique invite link for the current group and returns it.
     */
    suspend fun generateInviteLink(): String? {
        val groupId = _selectedGroupId.value ?: return null
        val userId = _currentUserId.value ?: return null

        val token = UUID.randomUUID().toString()
        val expiresAt = LocalDateTime.now().plusHours(AppConstants.INVITE_EXPIRY_HOURS.toLong())
        val expiresAtStr = expiresAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        val invite = GroupInvite(
            groupId = groupId,
            createdBy = userId,
            token = token,
            expiresAt = expiresAtStr,
            used = false
        )

        val res = groupInviteRepository.createInvite(invite)
        if (res is DataResult.Success) {
            _memberRefreshTrigger.value++
            return "repartija://join?token=$token"
        } else {
            _actionResult.value = "Error al crear invitación"
            return null
        }
    }

    fun deleteInvite(inviteId: String) {
        val groupId = _selectedGroupId.value ?: return
        viewModelScope.launch {
            groupInviteRepository.deleteInvite(inviteId, groupId)
            _memberRefreshTrigger.value++
        }
    }

    fun processPendingToken(token: String) {
        val userId = _currentUserId.value ?: return
        viewModelScope.launch {
            val inviteRes = groupInviteRepository.getInviteByToken(token)
            if (inviteRes is DataResult.Success) {
                handleValidInvite(inviteRes.data, userId)
            } else if (inviteRes is DataResult.Error) {
                _actionResult.value = "Invitación no encontrada o inválida."
            }
        }
    }

    private suspend fun handleValidInvite(invite: GroupInvite, userId: String) {
        if (invite.used) {
            _actionResult.value = "El enlace de invitación ya ha sido utilizado."
            return
        }

        if (isInviteExpired(invite)) {
            _actionResult.value = "El enlace de invitación ha expirado."
            return
        }

        joinGroupViaInvite(invite, userId)
    }

    private fun isInviteExpired(invite: GroupInvite): Boolean {
        return try {
            val expirationDate = LocalDateTime.parse(invite.expiresAt, DateTimeFormatter.ISO_DATE_TIME)
            LocalDateTime.now().isAfter(expirationDate)
        } catch (e: Exception) {
            true
        }
    }

    private suspend fun joinGroupViaInvite(invite: GroupInvite, userId: String) {
        val addRes = groupMemberRepository.addMember(GroupMember(groupId = invite.groupId, userId = userId))
        if (addRes is DataResult.Success) {
            groupInviteRepository.markInviteAsUsed(invite.id)
            groupRepository.fetchGroupsForUser(userId)
            _selectedGroupId.value = invite.groupId
            _actionResult.value = "Te uniste al grupo exitosamente."
        } else {
            _actionResult.value = "Error al unirse al grupo."
        }
    }
    fun deleteExpense(expenseId: String) {
        val groupId = _selectedGroupId.value ?: return
        viewModelScope.launch {
            // Splits will be deleted by cascade if DB is configured, but let's be explicit if not sure.
            // Requirement says: "delete from expenses table (cascade deletes splits)"
            val res = expenseRepository.deleteExpense(expenseId, groupId)
            if (res is DataResult.Error) {
                _actionResult.value = res.message
            }
            _refreshTrigger.value++
        }
    }

    fun deletePayment(paymentId: String) {
        val groupId = _selectedGroupId.value ?: return
        viewModelScope.launch {
            val res = paymentRepository.deletePayment(paymentId, groupId)
            if (res is DataResult.Error) {
                _actionResult.value = res.message
            }
            _refreshTrigger.value++
        }
    }

    fun updateExpense(expense: Expense, newDescription: String, newAmount: Double) {
        val groupId = _selectedGroupId.value ?: return
        viewModelScope.launch {
            val updatedExpense = expense.copy(description = newDescription, amount = newAmount)
            val res = expenseRepository.updateExpense(updatedExpense)
            
            if (res is DataResult.Success) {
                handleExpenseAmountChange(expense, newAmount)
            } else if (res is DataResult.Error) {
                _actionResult.value = res.message
            }
            _refreshTrigger.value++
        }
    }

    private suspend fun handleExpenseAmountChange(oldExpense: Expense, newAmount: Double) {
        if (oldExpense.amount == newAmount) return
        
        val oldAmount = oldExpense.amount
        val ratio = if (oldAmount > 0) newAmount / oldAmount else 1.0
        
        val splitsRes = expenseSplitRepository.fetchSplitsForExpense(oldExpense.id)
        if (splitsRes is DataResult.Success) {
            val updatedSplits = splitsRes.data.map { split ->
                split.copy(amount = split.amount * ratio)
            }
            // Delete old and insert new to be sure
            expenseSplitRepository.deleteSplitsForExpense(oldExpense.id)
            expenseSplitRepository.addSplits(updatedSplits)
        }
    }

    fun updatePayment(payment: Payment, newAmount: Double) {
        val groupId = _selectedGroupId.value ?: return
        viewModelScope.launch {
            val updatedPayment = payment.copy(amount = newAmount)
            val res = paymentRepository.updatePayment(updatedPayment)
            if (res is DataResult.Error) {
                _actionResult.value = res.message
            }
            _refreshTrigger.value++
        }
    }
}
