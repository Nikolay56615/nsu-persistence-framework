package ru.nsu.exception

class UnsupportedTypeException(typeName: String, details: String? = null) : PersistenceException(
    buildString {
        append("Unsupported type: ")
        append(typeName)
        if (!details.isNullOrBlank()) {
            append(" (")
            append(details)
            append(')')
        }
    }
)
