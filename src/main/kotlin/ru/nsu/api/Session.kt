package ru.nsu.api

import ru.nsu.PersistFilter
import java.nio.file.Path
import kotlin.reflect.KClass

interface Session {
    fun setDirectory(path: Path): Session
    fun insert(obj: Any): Session
    fun <T : Any> find(clazz: KClass<T>): List<T>
    fun <T : Any> find(clazz: KClass<T>, filter: PersistFilter): List<T>
    fun <T : Any> delete(clazz: KClass<T>, filter: PersistFilter): Session
    fun persist()
}