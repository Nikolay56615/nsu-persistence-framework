package ru.nsu.core

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import ru.nsu.annotation.Persistable
import ru.nsu.exception.InvalidMapKeyTypeException
import ru.nsu.exception.SerializationException
import ru.nsu.exception.UnsupportedTypeException
import java.lang.reflect.Array
import java.math.BigDecimal

internal class JsonValueEncoder(
    private val objectMapper: ObjectMapper
) {
    fun encode(value: Any?): JsonNode = encodeValue(value)

    private fun encodeValue(value: Any?): JsonNode {
        if (value == null) {
            return NullNode.instance
        }

        return when (value) {
            is String -> TextNode(value)
            is Char -> TextNode(value.toString())
            is Boolean -> BooleanNode.valueOf(value)
            is Byte, is Short, is Int, is Long, is Float, is Double, is BigDecimal ->
                objectMapper.nodeFactory.numberNode(value.toString().toBigDecimal())

            is Enum<*> -> TextNode(value.name)
            is Iterable<*> -> {
                val arrayNode = objectMapper.createArrayNode()
                value.forEach { arrayNode.add(encodeValue(it)) }
                arrayNode
            }

            is Map<*, *> -> {
                val objectNode = objectMapper.createObjectNode()
                value.forEach { (key, itemValue) ->
                    val keyLiteral = keyToString(key)
                    objectNode.set<JsonNode>(keyLiteral, encodeValue(itemValue))
                }
                objectNode
            }

            else -> {
                val clazz = value.javaClass
                when {
                    clazz.isArray -> encodeArray(value)
                    clazz.isAnnotationPresent(Persistable::class.java) -> encodeObject(value)
                    else -> throw UnsupportedTypeException(clazz.name)
                }
            }
        }
    }

    private fun keyToString(key: Any?): String {
        if (key == null) {
            throw InvalidMapKeyTypeException("null")
        }

        return when (key) {
            is String -> key
            is Char -> key.toString()
            is Boolean, is Byte, is Short, is Int, is Long, is Float, is Double, is BigDecimal -> key.toString()
            is Enum<*> -> key.name
            else -> throw InvalidMapKeyTypeException(key.javaClass.name)
        }
    }

    private fun encodeArray(value: Any): ArrayNode {
        val length = Array.getLength(value)
        val arrayNode = objectMapper.createArrayNode()
        for (index in 0 until length) {
            arrayNode.add(encodeValue(Array.get(value, index)))
        }
        return arrayNode
    }

    private fun encodeObject(value: Any): ObjectNode {
        val meta = PersistClassIntrospector.getMeta(value.javaClass)
        val objectNode = objectMapper.createObjectNode()
        for (field in meta.fields) {
            val fieldValue = try {
                field.field.get(value)
            } catch (ex: Exception) {
                throw SerializationException(
                    "Failed to read field '${field.fieldName}' from ${value.javaClass.name}: ${ex.message}",
                    ex
                )
            }
            objectNode.set<JsonNode>(field.jsonName, encodeValue(fieldValue))
        }
        return objectNode
    }
}
