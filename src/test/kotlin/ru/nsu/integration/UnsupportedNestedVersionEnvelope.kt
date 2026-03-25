package ru.nsu.integration

import ru.nsu.annotation.PersistField
import ru.nsu.annotation.Persistable

@Persistable(version = 2)
data class UnsupportedNestedVersionEnvelope(
    @field:PersistField
    val id: String,
    @field:PersistField
    val legacy: LegacyNested
) {
    @Persistable
    data class LegacyNested(
        @field:PersistField
        val value: String
    )
}
