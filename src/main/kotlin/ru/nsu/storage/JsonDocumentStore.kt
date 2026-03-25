package ru.nsu.storage

import com.fasterxml.jackson.databind.JsonNode
import java.nio.file.Path
import java.util.UUID
import kotlin.reflect.KClass

interface JsonDocumentStore {
    fun setDirectory(path: Path)
    fun readAll(clazz: KClass<*>): List<JsonNode>
    fun write(clazz: KClass<*>, documentId: UUID, node: JsonNode)
    fun deleteMatching(clazz: KClass<*>, predicate: (JsonNode) -> Boolean)
}
