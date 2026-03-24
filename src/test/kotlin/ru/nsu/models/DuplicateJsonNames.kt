package ru.nsu.models

import ru.nsu.annotation.PersistField
import ru.nsu.annotation.Persistable

@Persistable
data class DuplicateJsonNames(
    @field:PersistField(name = "same")
    val first: String,
    @field:PersistField(name = "same")
    val second: String
)
