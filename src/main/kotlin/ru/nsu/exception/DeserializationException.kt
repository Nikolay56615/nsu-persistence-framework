package ru.nsu.exception

class DeserializationException(message: String, cause: Throwable? = null) : PersistenceException(message, cause)
