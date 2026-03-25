package ru.nsu.fixture

import ru.nsu.annotation.PersistField
import ru.nsu.annotation.Persistable

@Persistable(version = 2)
data class InvalidVersionUntilRecord(
    @field:PersistField(until = 3)
    val id: String
)
