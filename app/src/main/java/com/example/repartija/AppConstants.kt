package com.example.repartija

object AppConstants {
    const val INVITE_EXPIRY_HOURS = 72
    const val SEARCH_DEBOUNCE_MS = 300L
    const val BALANCE_THRESHOLD = 0.01

    object Tables {
        const val PROFILES = "profiles"
        const val GROUPS = "groups"
        const val GROUP_MEMBERS = "group_members"
        const val GROUP_INVITES = "group_invites"
        const val EXPENSES = "expenses"
        const val EXPENSE_SPLITS = "expense_splits"
        const val PAYMENTS = "payments"
    }
}
