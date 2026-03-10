package ru.nsu.models

import ru.nsu.annotation.PersistIgnore
import ru.nsu.annotation.PersistName
import ru.nsu.annotation.Persistable

@Persistable
data class AnnotatedRecord(
    val id: String,
    @field:PersistName("display_name")
    val name: String,
    @field:PersistIgnore
    val secret: String = "hidden"
)
