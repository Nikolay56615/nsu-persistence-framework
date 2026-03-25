package ru.nsu.integration

import ru.nsu.annotation.PersistField
import ru.nsu.annotation.Persistable

@Persistable(version = 2)
data class InvalidRequiredVersionedRecord(
    @field:PersistField(since = 2)
    val id: String
)
