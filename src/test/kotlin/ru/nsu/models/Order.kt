package ru.nsu.models

import ru.nsu.annotation.Persistable

@Persistable
data class Order(
    val id: String,
    val userId: String,
    val status: OrderStatus,
    val items: List<OrderItem>,
    val metadata: Map<String, String>
)