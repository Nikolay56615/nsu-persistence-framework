package ru.nsu.fixture

import ru.nsu.annotation.PersistField
import ru.nsu.annotation.Persistable

@Persistable
data class Address(
    @field:PersistField
    val city: String,
    @field:PersistField
    val street: String,
    @field:PersistField
    val zipCode: String
)
