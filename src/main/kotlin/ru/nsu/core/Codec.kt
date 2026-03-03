package ru.nsu.core

import com.fasterxml.jackson.databind.JsonNode
import java.lang.reflect.Type
import java.nio.file.Path
import kotlin.reflect.KClass

interface Codec {
    fun parse(json: String): JsonNode
    fun parse(file: Path): JsonNode
    fun write(node: JsonNode): String
    fun writePretty(node: JsonNode): String
    fun writeToFile(node: JsonNode, file: Path)
    fun toJsonNode(value: Any?): JsonNode
    fun <T : Any> decodeToClass(node: JsonNode, clazz: KClass<T>): T
    fun decode(node: JsonNode, type: Type): Any?
}