package ru.nsu.codec

import com.fasterxml.jackson.databind.JsonNode
import java.lang.reflect.Type
import java.nio.file.Path
import kotlin.reflect.KClass

interface DocumentCodec {
    fun parse(json: String): JsonNode
    fun parse(file: Path): JsonNode
    fun write(node: JsonNode): String
    fun writePretty(node: JsonNode): String
    fun writeToFile(node: JsonNode, file: Path)
    fun toJsonNode(value: Any?): JsonNode
    fun toJsonNode(value: Any?, version: Int): JsonNode
    fun <T : Any> decodeToClass(node: JsonNode, clazz: KClass<T>, expectedVersion: Int? = null): T
    fun decode(node: JsonNode, type: Type): Any?
}
