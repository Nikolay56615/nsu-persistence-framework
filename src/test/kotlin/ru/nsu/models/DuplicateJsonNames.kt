package ru.nsu.models

import ru.nsu.annotation.PersistName
import ru.nsu.annotation.Persistable

@Persistable
data class DuplicateJsonNames(
    @field:PersistName("same")
    val first: String,
    @field:PersistName("same")
    val second: String
)
