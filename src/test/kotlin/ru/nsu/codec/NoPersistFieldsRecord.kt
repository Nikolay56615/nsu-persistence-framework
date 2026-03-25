package ru.nsu.codec

import ru.nsu.annotation.Persistable

@Persistable
data class NoPersistFieldsRecord(
    val id: String
)
