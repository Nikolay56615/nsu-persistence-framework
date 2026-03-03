package ru.nsu.core

import com.fasterxml.jackson.databind.JsonNode
import ru.nsu.api.Deserializer
import kotlin.reflect.KClass

class JsonDeserializer<T : Any>(
    private val clazz: KClass<T>,
    private val sourceJson: String,
    private val codec: Codec
) : Deserializer<T> {

    override fun instance(): T {
        val node = codec.parse(sourceJson)
        if (!node.isObject) {
            throw Exception("Expected JSON object for ${clazz.qualifiedName}.instance()")
        }
        return codec.decodeToClass(node, clazz)
    }

    override fun collection(): List<T> {
        val node = codec.parse(sourceJson)
        if (!node.isArray) {
            throw Exception("Expected JSON array for ${clazz.qualifiedName}.collection()")
        }
        return node.map { codec.decodeToClass(it, clazz) }
    }

    override fun map(keyClass: KClass<*>): Map<Any, T> {
        val node = codec.parse(sourceJson)
        if (!node.isObject) {
            throw Exception("Expected JSON object for ${clazz.qualifiedName}.map(..)")
        }
        val result = LinkedHashMap<Any, T>()
        node.fields().forEach { (keyLiteral, valueNode) ->
            val key = TypeUtils.stringToKey(keyLiteral, keyClass.java)
            result[key] = decodeMapValue(valueNode)
        }
        return result
    }

    private fun decodeMapValue(valueNode: JsonNode): T {
        if (!valueNode.isObject) {
            throw Exception("Map values must be JSON objects for ${clazz.qualifiedName}")
        }
        return codec.decodeToClass(valueNode, clazz)
    }
}
