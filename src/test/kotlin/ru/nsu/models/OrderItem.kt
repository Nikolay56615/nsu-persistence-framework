package ru.nsu.models

import ru.nsu.annotation.Persistable

@Persistable
data class OrderItem(
    val sku: String,
    val quantity: Int,
    val price: Double
)