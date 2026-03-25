package ru.nsu.demo

import ru.nsu.annotation.PersistField
import ru.nsu.annotation.Persistable

@Persistable(version = 2)
data class DemoAddress(
    @field:PersistField
    val city: String,

    @field:PersistField
    val street: String,

    @field:PersistField(since = 2)
    val zipCode: String? = null
)
