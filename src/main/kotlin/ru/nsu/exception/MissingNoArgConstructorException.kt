package ru.nsu.exception

class MissingNoArgConstructorException(className: String) :
    PersistenceException("Failed to instantiate $className: no suitable constructor found")
