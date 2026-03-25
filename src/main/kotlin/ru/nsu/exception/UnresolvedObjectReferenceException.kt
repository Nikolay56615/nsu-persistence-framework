package ru.nsu.exception

class UnresolvedObjectReferenceException(referenceId: String) : DeserializationException(
    "Unresolved object reference '$referenceId'. " +
        "The target object has not been created yet; cyclic graphs require the target class " +
        "to support pre-creation via an accessible no-arg constructor and mutable fields."
)
