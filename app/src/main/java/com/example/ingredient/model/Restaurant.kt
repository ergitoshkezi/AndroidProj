package com.example.ingredient.model

data class Restaurant(
    val restaurantId: String = "",
    val nomeRistorante: String = "",
    val indirizzo: String = "",
    val telefono: String = "",
    val tipoCucina: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val createdAt: Long = 0L
)
