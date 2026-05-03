package com.example.repartija.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import java.time.LocalDate

@Entity(tableName = "groups")
data class Group(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)

@Entity(tableName = "members")
data class Member(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val groupId: Int,
    val name: String
)

@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val groupId: Int,
    val description: String,
    val fromMemberId: Int,      // quien debe
    val toMemberId: Int,        // a quien le debe
    val originalAmount: Double,
    val currency: String,       // ARS/USD
    var remainingBalance: Double,
    val annualRate: Double,     // TNA ej: 30.0
    val startDate: LocalDate,
    var lastInterestDate: LocalDate
)

@Entity(tableName = "payments")
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val debtId: Int,
    val amount: Double,
    val date: LocalDate
)

class Converters {
    @TypeConverter
    fun fromTimestamp(value: String?): LocalDate? {
        return value?.let { LocalDate.parse(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: LocalDate?): String? {
        return date?.toString()
    }
}
