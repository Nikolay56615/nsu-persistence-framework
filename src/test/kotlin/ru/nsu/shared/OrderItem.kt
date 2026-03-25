package ru.nsu.shared

import ru.nsu.annotation.PersistField
import ru.nsu.annotation.Persistable

@Persistable
data class OrderItem(
    @field:PersistField
    val sku: String,
    @field:PersistField
    val quantity: Int,
    @field:PersistField
    val price: Double
)
