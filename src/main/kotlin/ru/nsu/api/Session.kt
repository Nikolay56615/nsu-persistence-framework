package ru.nsu.api

import ru.nsu.query.PersistFilter
import java.nio.file.Path
import kotlin.reflect.KClass

interface Session {
    fun setDirectory(path: Path): Session
    fun insert(obj: Any): Session
    fun insert(obj: Any, version: Int): Session
    fun <T : Any> find(clazz: KClass<T>): List<T>
    fun <T : Any> find(clazz: KClass<T>, expectedVersion: Int): List<T>
    fun <T : Any> find(clazz: KClass<T>, filter: PersistFilter): List<T>
    fun <T : Any> find(clazz: KClass<T>, filter: PersistFilter, expectedVersion: Int): List<T>
    fun <T : Any> delete(clazz: KClass<T>, filter: PersistFilter): Session
    fun persist()
}
