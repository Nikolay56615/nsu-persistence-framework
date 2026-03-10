package ru.nsu.exception

class InvalidMapKeyTypeException(typeName: String) :
    PersistenceException("Unsupported map key type: $typeName")
