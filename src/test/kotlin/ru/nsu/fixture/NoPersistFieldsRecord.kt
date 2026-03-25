package ru.nsu.fixture

import ru.nsu.annotation.Persistable

@Persistable
data class NoPersistFieldsRecord(
    val id: String
)
