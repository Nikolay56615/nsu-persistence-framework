package ru.nsu.codec

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import ru.nsu.exception.DeserializationException
import ru.nsu.exception.SerializationException
import ru.nsu.metadata.ReflectionPersistMetadataResolver
import java.lang.reflect.Type
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.KClass

class JsonCodec(
    private val objectMapper: ObjectMapper = ObjectMapper()
) : DocumentCodec {
    companion object {
        const val ID_FIELD = "\$id"
        const val REF_FIELD = "\$ref"
        const val VERSION_FIELD = "\$version"
    }

    private val metadataResolver = ReflectionPersistMetadataResolver
    private val encoder = JsonValueEncoder(objectMapper, metadataResolver)
    private val decoder = JsonValueDecoder(metadataResolver)

    override fun parse(json: String): JsonNode = try {
        objectMapper.readTree(json)
    } catch (ex: Exception) {
        throw DeserializationException("Failed to parse JSON: ${ex.message}", ex)
    }

    override fun parse(file: Path): JsonNode {
        val text = try {
            Files.readString(file)
        } catch (ex: Exception) {
            throw DeserializationException("Failed to read JSON file '$file': ${ex.message}", ex)
        }
        return parse(text)
    }

    override fun write(node: JsonNode): String = try {
        objectMapper.writeValueAsString(node)
    } catch (ex: Exception) {
        throw SerializationException("Failed to convert JSON tree to string: ${ex.message}", ex)
    }

    override fun writePretty(node: JsonNode): String = try {
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node)
    } catch (ex: Exception) {
        throw SerializationException("Failed to convert JSON tree to pretty string: ${ex.message}", ex)
    }

    override fun writeToFile(node: JsonNode, file: Path) {
        try {
            file.parent?.let { Files.createDirectories(it) }
            Files.writeString(file, writePretty(node))
        } catch (ex: Exception) {
            throw SerializationException("Failed to write JSON to '$file': ${ex.message}", ex)
        }
    }

    override fun toJsonNode(value: Any?): JsonNode = encoder.encode(value)

    override fun toJsonNode(value: Any?, version: Int): JsonNode = encoder.encode(value, version)

    override fun <T : Any> decodeToClass(node: JsonNode, clazz: KClass<T>, expectedVersion: Int?): T {
        val result = decoder.decodeRoot(node, clazz.java, expectedVersion)
        if (!clazz.java.isInstance(result)) {
            throw DeserializationException("Decoded value is not of expected type ${clazz.qualifiedName}")
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    override fun decode(node: JsonNode, type: Type): Any? = decoder.decode(node, type)
}
