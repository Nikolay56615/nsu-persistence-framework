package ru.nsu.models

import ru.nsu.annotation.PersistField
import ru.nsu.annotation.Persistable

@Persistable
data class User(
    @field:PersistField
    val id: String,
    @field:PersistField
    val name: String,
    @field:PersistField
    val age: Int,
    @field:PersistField
    val address: Address,
    @field:PersistField(name = "is_active")
    val active: Boolean,
    @field:PersistField
    val tags: Set<String>
)
