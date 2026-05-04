package com.example.repartija.data.repository

import android.util.Log
import com.example.repartija.AppConstants.Tables
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central manager for Supabase Realtime channel subscriptions.
 * Subscribes to expenses, payments, expense_splits, and group_members tables
 * for the currently active group. Emits events that repositories consume to refresh data.
 */
@Singleton
class RealtimeManager @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentChannel: RealtimeChannel? = null
    private var subscriptionJob: Job? = null

    /** True while a realtime event is being processed (data re-fetching). */
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /** Emits the table name that changed so repositories can re-fetch. */
    private val _tableChanged = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val tableChanged: SharedFlow<String> = _tableChanged.asSharedFlow()

    fun setSyncing(syncing: Boolean) {
        _isSyncing.value = syncing
    }

    /**
     * Subscribe to realtime changes for the given group.
     * Unsubscribes from any previous group channel first.
     */
    fun subscribeToGroup(groupId: String) {
        // Tear down previous subscription
        unsubscribe()

        subscriptionJob = scope.launch {
            try {
                val channel = supabaseClient.realtime.channel("group_$groupId")
                currentChannel = channel

                // Listen to expenses table
                channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = Tables.EXPENSES
                }.onEach {
                    Log.d("RealtimeManager", "Expenses change: ${it::class.simpleName}")
                    _isSyncing.value = true
                    _tableChanged.emit(Tables.EXPENSES)
                }.launchIn(this)

                // Listen to expense_splits table
                channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = Tables.EXPENSE_SPLITS
                }.onEach {
                    Log.d("RealtimeManager", "Splits change: ${it::class.simpleName}")
                    _isSyncing.value = true
                    _tableChanged.emit(Tables.EXPENSE_SPLITS)
                }.launchIn(this)

                // Listen to payments table
                channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = Tables.PAYMENTS
                }.onEach {
                    Log.d("RealtimeManager", "Payments change: ${it::class.simpleName}")
                    _isSyncing.value = true
                    _tableChanged.emit(Tables.PAYMENTS)
                }.launchIn(this)

                // Listen to group_members table
                channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = Tables.GROUP_MEMBERS
                }.onEach {
                    Log.d("RealtimeManager", "Members change: ${it::class.simpleName}")
                    _isSyncing.value = true
                    _tableChanged.emit(Tables.GROUP_MEMBERS)
                }.launchIn(this)

                // Listen to expense_payers table
                channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = Tables.EXPENSE_PAYERS
                }.onEach {
                    Log.d("RealtimeManager", "Payers change: ${it::class.simpleName}")
                    _isSyncing.value = true
                    _tableChanged.emit(Tables.EXPENSE_PAYERS)
                }.launchIn(this)

                // Listen to profiles table
                channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = Tables.PROFILES
                }.onEach {
                    Log.d("RealtimeManager", "Profile change: ${it::class.simpleName}")
                    _isSyncing.value = true
                    _tableChanged.emit(Tables.PROFILES)
                }.launchIn(this)

                // Start the subscription
                channel.subscribe()
                Log.d("RealtimeManager", "Subscribed to group $groupId")
            } catch (e: Exception) {
                Log.e("RealtimeManager", "Failed to subscribe", e)
            }
        }
    }

    fun unsubscribe() {
        subscriptionJob?.cancel()
        subscriptionJob = null
        scope.launch {
            try {
                currentChannel?.unsubscribe()
                currentChannel = null
            } catch (e: Exception) {
                Log.e("RealtimeManager", "Failed to unsubscribe", e)
            }
        }
    }
}
