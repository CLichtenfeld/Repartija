package com.example.repartija.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    val email: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Group(
    val id: String = "",
    val name: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class GroupMember(
    @SerialName("group_id") val groupId: String,
    @SerialName("user_id") val userId: String
)

@Serializable
data class GroupInvite(
    val id: String = "",
    @SerialName("group_id") val groupId: String,
    @SerialName("created_by") val createdBy: String,
    val token: String,
    @SerialName("expires_at") val expiresAt: String,
    val used: Boolean = false
)

@Serializable
data class Expense(
    val id: String = "",
    @SerialName("group_id") val groupId: String,
    @SerialName("paid_by") val paidBy: String,
    val amount: Double,
    val description: String,
    val date: String,
    @SerialName("split_type") val splitType: String = "EQUALLY"
)

@Serializable
data class ExpensePayer(
    @SerialName("expense_id") val expenseId: String,
    @SerialName("user_id") val userId: String,
    val amount: Double
)

@Serializable
data class ExpenseSplit(
    @SerialName("expense_id") val expenseId: String,
    @SerialName("user_id") val userId: String,
    val amount: Double
)

@Serializable
data class Payment(
    val id: String = "",
    @SerialName("group_id") val groupId: String,
    @SerialName("from_user") val fromUser: String,
    @SerialName("to_user") val toUser: String,
    val amount: Double,
    val date: String
)
