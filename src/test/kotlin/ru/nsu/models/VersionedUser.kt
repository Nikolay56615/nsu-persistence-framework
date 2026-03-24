package ru.nsu.models

import ru.nsu.annotation.PersistField
import ru.nsu.annotation.Persistable

@Persistable(version = 3)
data class VersionedUser(
    @field:PersistField
    val id: String,
    @field:PersistField(until = 2)
    val nickname: String? = null,
    @field:PersistField(since = 2)
    val email: String? = null,
    @field:PersistField
    val address: VersionedAddress
)
