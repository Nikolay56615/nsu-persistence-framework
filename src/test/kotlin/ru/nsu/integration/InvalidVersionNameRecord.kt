package ru.nsu.integration

import ru.nsu.annotation.PersistField
import ru.nsu.annotation.Persistable

@Persistable(version = 2)
data class InvalidVersionNameRecord(
    @field:PersistField(name = "\$version")
    val id: String
)
