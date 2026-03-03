package ru.nsu.api

import ru.nsu.PersistFilter
import java.nio.file.Path

interface SerialStream<T : Any> {
    fun add(json: String): SerialStream<T>
    fun add(file: Path): SerialStream<T>
    fun toList(): List<T>
    fun toList(filter: PersistFilter): List<T>
    fun toListExclude(filter: PersistFilter): List<T>
}
