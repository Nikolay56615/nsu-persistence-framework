package ru.nsu.models

import ru.nsu.annotation.Persistable

@Persistable
data class Address(
    val city: String,
    val street: String,
    val zipCode: String
)