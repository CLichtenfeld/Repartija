package com.example.repartija.domain

import com.example.repartija.data.Member

data class SettlementSuggestion(
    val fromMember: Member,
    val toMember: Member,
    val amount: Double,
    val currency: String = "ARS"
)
