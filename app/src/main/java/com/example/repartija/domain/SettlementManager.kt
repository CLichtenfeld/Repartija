package com.example.repartija.domain

import com.example.repartija.data.Debt
import com.example.repartija.data.Member
import kotlin.math.abs
import kotlin.math.min

object SettlementManager {

    /**
     * Calcula quién debería pagarle a quién para minimizar transferencias.
     * Importante: Esto es una SUGERENCIA. Los usuarios deben pactar si
     * aceptan saldar deudas de esta manera, especialmente considerando
     * que diferentes deudas pueden tener diferentes TNA.
     */
    fun calculateSuggestions(debts: List<Debt>, members: List<Member>): List<SettlementSuggestion> {
        val balances = members.associateWith { 0.0 }.toMutableMap()

        // Calcular el balance neto de cada persona
        for (debt in debts) {
            val fromMember = members.find { it.id == debt.fromMemberId }
            val toMember = members.find { it.id == debt.toMemberId }
            
            if (fromMember != null && toMember != null) {
                balances[fromMember] = balances[fromMember]!! - debt.remainingBalance
                balances[toMember] = balances[toMember]!! + debt.remainingBalance
            }
        }

        val debtors = balances.filter { it.value < -0.01 }
            .map { it.key to abs(it.value) }
            .sortedByDescending { it.second }
            .toMutableList()

        val creditors = balances.filter { it.value > 0.01 }
            .map { it.key to it.value }
            .sortedByDescending { it.second }
            .toMutableList()

        val suggestions = mutableListOf<SettlementSuggestion>()

        var dIdx = 0
        var cIdx = 0

        while (dIdx < debtors.size && cIdx < creditors.size) {
            val (debtor, dAmt) = debtors[dIdx]
            val (creditor, cAmt) = creditors[cIdx]

            val settlementAmount = min(dAmt, cAmt)
            suggestions.add(SettlementSuggestion(debtor, creditor, settlementAmount))

            debtors[dIdx] = debtor to (dAmt - settlementAmount)
            creditors[cIdx] = creditor to (cAmt - settlementAmount)

            if (debtors[dIdx].second < 0.01) dIdx++
            if (creditors[cIdx].second < 0.01) cIdx++
        }

        return suggestions
    }
}
