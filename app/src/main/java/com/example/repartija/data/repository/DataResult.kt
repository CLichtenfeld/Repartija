package com.example.repartija.data.repository

sealed interface DataResult<out T> {
    data class Success<out T>(val data: T) : DataResult<T>
    data class Error(val message: String, val exception: Exception? = null) : DataResult<Nothing>
    data object Loading : DataResult<Nothing>
}
