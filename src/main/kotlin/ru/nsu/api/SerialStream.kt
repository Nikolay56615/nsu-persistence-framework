package ru.nsu.api

import ru.nsu.query.PersistFilter
import java.nio.file.Path

interface SerialStream<T : Any> {
    fun add(json: String): SerialStream<T>
    fun add(file: Path): SerialStream<T>
    fun toList(): List<T>
    fun toList(expectedVersion: Int): List<T>
    fun toList(filter: PersistFilter): List<T>
    fun toList(filter: PersistFilter, expectedVersion: Int): List<T>
    fun toListExclude(filter: PersistFilter): List<T>
    fun toListExclude(filter: PersistFilter, expectedVersion: Int): List<T>
}
