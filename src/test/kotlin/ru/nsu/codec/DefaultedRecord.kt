package ru.nsu.codec

import ru.nsu.annotation.PersistField
import ru.nsu.annotation.Persistable

@Persistable
data class DefaultedRecord(
    @field:PersistField
    val required: String,
    val count: Int = 7
)
