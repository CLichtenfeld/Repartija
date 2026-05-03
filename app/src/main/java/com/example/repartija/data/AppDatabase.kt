package com.example.repartija.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups")
    fun getAllGroups(): Flow<List<Group>>

    @Insert
    suspend fun insertGroup(group: Group)
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts WHERE groupId = :groupId")
    fun getDebtsByGroup(groupId: Int): Flow<List<Debt>>

    @Insert
    suspend fun insertDebt(debt: Debt)

    @Update
    suspend fun updateDebt(debt: Debt)

    @Delete
    suspend fun deleteDebt(debt: Debt)

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun getDebtById(id: Int): Debt?
}

@Dao
interface MemberDao {
    @Query("SELECT * FROM members WHERE groupId = :groupId")
    fun getMembersByGroup(groupId: Int): Flow<List<Member>>

    @Insert
    suspend fun insertMember(member: Member)

    @Update
    suspend fun updateMember(member: Member)

    @Delete
    suspend fun deleteMember(member: Member)
}

@Dao
interface PaymentDao {
    @Insert
    suspend fun insertPayment(payment: Payment)

    @Update
    suspend fun updatePayment(payment: Payment)

    @Delete
    suspend fun deletePayment(payment: Payment)

    @Query("SELECT * FROM payments WHERE debtId = :debtId ORDER BY date DESC")
    fun getPaymentsForDebt(debtId: Int): Flow<List<Payment>>

    @Query("SELECT p.* FROM payments p INNER JOIN debts d ON p.debtId = d.id WHERE d.groupId = :groupId ORDER BY p.date DESC")
    fun getPaymentsByGroup(groupId: Int): Flow<List<Payment>>
}

@Database(entities = [Group::class, Member::class, Debt::class, Payment::class], version = 2)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun debtDao(): DebtDao
    abstract fun memberDao(): MemberDao
    abstract fun paymentDao(): PaymentDao
}
