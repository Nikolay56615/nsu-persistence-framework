package ru.nsu.demo

import ru.nsu.annotation.PersistField
import ru.nsu.annotation.Persistable

@Persistable(version = 2)
data class DemoUser(
    @field:PersistField
    val id: String,

    @field:PersistField
    val name: String,

    @field:PersistField(name = "is_active")
    val active: Boolean,

    @field:PersistField
    val address: DemoAddress,

    @field:PersistField
    val tags: Set<String>,

    @field:PersistField(since = 2)
    val email: String? = null
) {
    val label: String
        get() = "$name <$id>"
}
