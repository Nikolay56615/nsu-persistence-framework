package ru.nsu.models

import ru.nsu.annotation.PersistName
import ru.nsu.annotation.Persistable

@Persistable
data class User(
    val id: String,
    val name: String,
    val age: Int,
    val address: Address,
    @field:PersistName("is_active")
    val active: Boolean,
    val tags: Set<String>
)
