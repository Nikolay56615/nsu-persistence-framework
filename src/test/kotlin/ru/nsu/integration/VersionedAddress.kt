package ru.nsu.integration

import ru.nsu.annotation.PersistField
import ru.nsu.annotation.Persistable

@Persistable(version = 3)
data class VersionedAddress(
    @field:PersistField
    val city: String,
    @field:PersistField(name = "zip_code", since = 2)
    val zipCode: String? = null
)
