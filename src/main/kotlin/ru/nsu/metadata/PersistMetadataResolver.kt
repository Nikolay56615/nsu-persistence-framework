package ru.nsu.metadata

internal interface PersistMetadataResolver {
    fun isPersistable(clazz: Class<*>): Boolean
    fun requirePersistable(clazz: Class<*>)
    fun getMeta(clazz: Class<*>): PersistClassMeta
}
