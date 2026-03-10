package ru.nsu.models

import ru.nsu.annotation.Persistable

@Persistable
data class DefaultedRecord(
    val required: String,
    val count: Int = 7
)
