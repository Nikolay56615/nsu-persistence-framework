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
import java.util.IdentityHashMap

internal class JsonValueEncoder(
    private val objectMapper: ObjectMapper
) {
    private class SerializationContext {
        private val objectIds = IdentityHashMap<Any, String>()
        private var nextId = 1L

        fun existingId(value: Any): String? = objectIds[value]

        fun register(value: Any): String {
            val id = nextId.toString()
            nextId += 1
            objectIds[value] = id
            return id
        }
    }

    fun encode(value: Any?): JsonNode = encodeValue(value, SerializationContext())

    private fun encodeValue(value: Any?, context: SerializationContext): JsonNode {
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
                value.forEach { arrayNode.add(encodeValue(it, context)) }
                arrayNode
            }

            is Map<*, *> -> {
                val objectNode = objectMapper.createObjectNode()
                value.forEach { (key, itemValue) ->
                    val keyLiteral = keyToString(key)
                    objectNode.set<JsonNode>(keyLiteral, encodeValue(itemValue, context))
                }
                objectNode
            }

            else -> {
                val clazz = value.javaClass
                when {
                    clazz.isArray -> encodeArray(value, context)
                    clazz.isAnnotationPresent(Persistable::class.java) -> encodeObject(value, context)
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

    private fun encodeArray(value: Any, context: SerializationContext): ArrayNode {
        val length = Array.getLength(value)
        val arrayNode = objectMapper.createArrayNode()
        for (index in 0 until length) {
            arrayNode.add(encodeValue(Array.get(value, index), context))
        }
        return arrayNode
    }

    private fun encodeObject(value: Any, context: SerializationContext): ObjectNode {
        val existingId = context.existingId(value)
        if (existingId != null) {
            return objectMapper.createObjectNode().put(Codec.REF_FIELD, existingId)
        }

        val objectId = context.register(value)
        val meta = PersistClassIntrospector.getMeta(value.javaClass)
        val objectNode = objectMapper.createObjectNode()
        objectNode.put(Codec.ID_FIELD, objectId)
        for (field in meta.fields) {
            val fieldValue = try {
                field.field.get(value)
            } catch (ex: Exception) {
                throw SerializationException(
                    "Failed to read field '${field.fieldName}' from ${value.javaClass.name}: ${ex.message}",
                    ex
                )
            }
            objectNode.set<JsonNode>(field.jsonName, encodeValue(fieldValue, context))
        }
        return objectNode
    }
}
