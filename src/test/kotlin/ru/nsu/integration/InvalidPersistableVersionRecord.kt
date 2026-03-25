package ru.nsu.integration

import ru.nsu.annotation.PersistField
import ru.nsu.annotation.Persistable

@Persistable(version = 0)
data class InvalidPersistableVersionRecord(
    @field:PersistField
    val id: String
)
