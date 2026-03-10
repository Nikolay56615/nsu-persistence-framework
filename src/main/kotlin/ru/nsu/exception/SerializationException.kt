package ru.nsu.exception

class SerializationException(message: String, cause: Throwable? = null) : PersistenceException(message, cause)
