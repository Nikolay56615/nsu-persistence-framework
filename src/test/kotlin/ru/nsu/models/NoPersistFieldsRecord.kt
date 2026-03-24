package ru.nsu.models

import ru.nsu.annotation.Persistable

@Persistable
data class NoPersistFieldsRecord(
    val id: String
)
