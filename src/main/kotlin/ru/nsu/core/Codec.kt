package ru.nsu.core

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.lang.reflect.Type
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.KClass

class Codec(
    private val objectMapper: ObjectMapper = ObjectMapper()
) {
    private val encoder = JsonValueEncoder(objectMapper)
    private val decoder = JsonValueDecoder()

    fun parse(json: String): JsonNode = try {
        objectMapper.readTree(json)
    } catch (ex: Exception) {
        throw RuntimeException("Failed to parse JSON: ${ex.message}", ex)
    }

    fun parse(file: Path): JsonNode {
        val text = try {
            Files.readString(file)
        } catch (ex: Exception) {
            throw RuntimeException("Failed to read JSON file '$file': ${ex.message}", ex)
        }
        return parse(text)
    }

    fun write(node: JsonNode): String = try {
        objectMapper.writeValueAsString(node)
    } catch (ex: Exception) {
        throw RuntimeException("Failed to convert JSON tree to string: ${ex.message}", ex)
    }

    fun writePretty(node: JsonNode): String = try {
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node)
    } catch (ex: Exception) {
        throw RuntimeException("Failed to convert JSON tree to pretty string: ${ex.message}", ex)
    }

    fun writeToFile(node: JsonNode, file: Path) {
        try {
            file.parent?.let { Files.createDirectories(it) }
            Files.writeString(file, writePretty(node))
        } catch (ex: Exception) {
            throw RuntimeException("Failed to write JSON to '$file': ${ex.message}", ex)
        }
    }

    fun toJsonNode(value: Any?): JsonNode = encoder.encode(value)

    fun <T : Any> decodeToClass(node: JsonNode, clazz: KClass<T>): T {
        val result = decode(node, clazz.java)
        if (!clazz.java.isInstance(result)) {
            throw RuntimeException("Decoded value is not of expected type ${clazz.qualifiedName}")
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    fun decode(node: JsonNode, type: Type): Any? = decoder.decode(node, type)
}
