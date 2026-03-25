package ru.nsu.fixture

import ru.nsu.annotation.PersistField
import ru.nsu.annotation.Persistable

@Persistable
data class DuplicateJsonNames(
    @field:PersistField(name = "same")
    val first: String,
    @field:PersistField(name = "same")
    val second: String
)
