package ru.nsu.fixture

import ru.nsu.annotation.PersistIgnore
import ru.nsu.annotation.PersistName
import ru.nsu.annotation.Persistable

@Persistable
data class LegacyAnnotatedRecord(
    @field:PersistName("legacy_name")
    val name: String,
    @field:PersistIgnore
    val secret: String = "hidden"
)
