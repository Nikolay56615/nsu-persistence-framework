package ru.nsu.exception

class MissingPersistableAnnotationException(className: String) :
    PersistenceException("Class '$className' must be annotated with @Persistable")
