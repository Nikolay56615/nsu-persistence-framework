package ru.nsu.codec

import ru.nsu.annotation.PersistField
import ru.nsu.annotation.Persistable
import ru.nsu.shared.OrderItem
import ru.nsu.shared.OrderStatus

@Persistable
data class Order(
    @field:PersistField
    val id: String,
    @field:PersistField
    val userId: String,
    @field:PersistField
    val status: OrderStatus,
    @field:PersistField
    val items: List<OrderItem>,
    @field:PersistField
    val metadata: Map<String, String>
)
