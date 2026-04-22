package com.example.ingredient.model

data class User(
    val id: String = "",
    val nome: String = "",
    val cognome: String = "",
    val email: String = "",
    val password: String = "",
    val userType: String = "",
    val allergeni: List<String> = emptyList(),
    val createdAt: Long = 0L
)
