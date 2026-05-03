package com.example.repartija.data

import com.example.repartija.domain.DebtManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.math.min

class DebtRepository(private val db: AppDatabase) {
    private val groupDao = db.groupDao()
    private val debtDao = db.debtDao()
    private val memberDao = db.memberDao()
    private val paymentDao = db.paymentDao()

    fun getAllGroups(): Flow<List<Group>> = groupDao.getAllGroups()
    suspend fun addGroup(group: Group) = groupDao.insertGroup(group)

    fun getDebtsByGroup(groupId: Int): Flow<List<Debt>> = debtDao.getDebtsByGroup(groupId)
    fun getMembersByGroup(groupId: Int): Flow<List<Member>> = memberDao.getMembersByGroup(groupId)
    
    fun getPaymentsByGroup(groupId: Int): Flow<List<Payment>> = paymentDao.getPaymentsByGroup(groupId)
    fun getPaymentsForDebt(debtId: Int): Flow<List<Payment>> = paymentDao.getPaymentsForDebt(debtId)

    suspend fun addMember(member: Member) = memberDao.insertMember(member)
    suspend fun updateMember(member: Member) = memberDao.updateMember(member)
    suspend fun deleteMember(member: Member) = memberDao.deleteMember(member)

    suspend fun addDebt(debt: Debt) = debtDao.insertDebt(debt)
    suspend fun updateDebt(debt: Debt) = debtDao.updateDebt(debt)
    suspend fun deleteDebt(debt: Debt) = debtDao.deleteDebt(debt)

    suspend fun deletePayment(payment: Payment) {
        val debt = debtDao.getDebtById(payment.debtId)
        if (debt != null) {
            // Restaurar el balance de la deuda al borrar el pago
            val updatedDebt = debt.copy(remainingBalance = debt.remainingBalance + payment.amount)
            debtDao.updateDebt(updatedDebt)
        }
        paymentDao.deletePayment(payment)
    }

    suspend fun updateDebtInterest(debtId: Int, currentDate: LocalDate) {
        val debt = debtDao.getDebtById(debtId) ?: return
        val updatedDebt = DebtManager.updateDailyInterest(debt, currentDate)
        debtDao.updateDebt(updatedDebt)
    }

    suspend fun registerPayment(debtId: Int, amount: Double, date: LocalDate) {
        val debt = debtDao.getDebtById(debtId) ?: return
        val updatedDebt = DebtManager.applyPayment(debt, amount, date)
        
        debtDao.updateDebt(updatedDebt)
        paymentDao.insertPayment(Payment(debtId = debtId, amount = amount, date = date))
    }

    suspend fun executeSettlement(fromMemberId: Int, toMemberId: Int, amount: Double, date: LocalDate, groupId: Int) {
        val allDebts = debtDao.getDebtsByGroup(groupId).first()
        for (debt in allDebts) {
            updateDebtInterest(debt.id, date)
        }

        val updatedDebts = debtDao.getDebtsByGroup(groupId).first()
        
        var remainingToPay = amount
        val debtsToPay = updatedDebts.filter { it.fromMemberId == fromMemberId && it.remainingBalance > 0 }
            .sortedByDescending { it.annualRate }

        for (debt in debtsToPay) {
            if (remainingToPay <= 0) break
            val payment = min(remainingToPay, debt.remainingBalance)
            registerPayment(debt.id, payment, date)
            remainingToPay -= payment
        }

        var remainingToReceive = amount
        val creditsToReceive = updatedDebts.filter { it.toMemberId == toMemberId && it.remainingBalance > 0 }
            .sortedBy { it.annualRate }

        for (debt in creditsToReceive) {
            if (remainingToReceive <= 0) break
            val deduction = min(remainingToReceive, debt.remainingBalance)
            registerPayment(debt.id, deduction, date)
            remainingToReceive -= deduction
        }
    }
}
