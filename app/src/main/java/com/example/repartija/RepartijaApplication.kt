package com.example.repartija

import android.app.Application
import androidx.room.Room
import com.example.repartija.data.AppDatabase
import com.example.repartija.data.DebtRepository

class RepartijaApplication : Application() {
    private val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "repartija_db")
            .fallbackToDestructiveMigration()
            .build()
    }
    val repository by lazy { DebtRepository(database) }
}
