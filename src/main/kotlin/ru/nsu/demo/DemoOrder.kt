package ru.nsu.demo

import ru.nsu.annotation.PersistField
import ru.nsu.annotation.Persistable

enum class DemoOrderStatus {
    NEW,
    PAID,
    CANCELLED
}

@Persistable
data class DemoOrderItem(
    @field:PersistField
    val sku: String,

    @field:PersistField
    val quantity: Int,

    @field:PersistField
    val price: Double
)

@Persistable
data class DemoOrder(
    @field:PersistField
    val id: String,

    @field:PersistField
    val userId: String,

    @field:PersistField
    val status: DemoOrderStatus,

    @field:PersistField
    val items: List<DemoOrderItem>,

    @field:PersistField
    val metadata: Map<String, String>
)
