package ru.nsu.integration

import ru.nsu.annotation.PersistField
import ru.nsu.annotation.Persistable

@Persistable(version = 2)
data class InvalidVersionFieldRangeRecord(
    @field:PersistField(since = 2, until = 1)
    val id: String
)
