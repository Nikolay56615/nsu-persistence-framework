package ru.nsu.codec

import ru.nsu.annotation.PersistField
import ru.nsu.annotation.Persistable

@Persistable
data class AnnotatedRecord(
    @field:PersistField
    val id: String,
    @field:PersistField(name = "display_name")
    val name: String,
    val secret: String = "hidden"
)
