package ru.nsu.exception

open class DeserializationException(message: String, cause: Throwable? = null) : PersistenceException(message, cause)
