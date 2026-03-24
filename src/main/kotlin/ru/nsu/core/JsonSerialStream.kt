package ru.nsu.core

import com.fasterxml.jackson.databind.JsonNode
import ru.nsu.PersistFilter
import ru.nsu.api.SerialStream
import java.nio.file.Path
import kotlin.reflect.KClass

class JsonSerialStream<T : Any>(
    private val clazz: KClass<T>,
    private val codec: Codec = Codec()
) : SerialStream<T> {
    private val documents = mutableListOf<JsonNode>()

    override fun add(json: String): SerialStream<T> {
        documents.add(codec.parse(json))
        return this
    }

    override fun add(file: Path): SerialStream<T> {
        documents.add(codec.parse(file))
        return this
    }

    override fun toList(): List<T> {
        return documents.map { codec.decodeToClass(it, clazz) }
    }

    override fun toList(expectedVersion: Int): List<T> {
        return documents.map { codec.decodeToClass(it, clazz, expectedVersion) }
    }

    override fun toList(filter: PersistFilter): List<T> {
        return documents
            .asSequence()
            .filter { filter.matches(it) }
            .map { codec.decodeToClass(it, clazz) }
            .toList()
    }

    override fun toList(filter: PersistFilter, expectedVersion: Int): List<T> {
        return documents
            .asSequence()
            .filter { filter.matches(it) }
            .map { codec.decodeToClass(it, clazz, expectedVersion) }
            .toList()
    }

    override fun toListExclude(filter: PersistFilter): List<T> {
        return documents
            .asSequence()
            .filterNot { filter.matches(it) }
            .map { codec.decodeToClass(it, clazz) }
            .toList()
    }

    override fun toListExclude(filter: PersistFilter, expectedVersion: Int): List<T> {
        return documents
            .asSequence()
            .filterNot { filter.matches(it) }
            .map { codec.decodeToClass(it, clazz, expectedVersion) }
            .toList()
    }
}
