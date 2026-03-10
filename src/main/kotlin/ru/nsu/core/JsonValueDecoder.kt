package ru.nsu.core

import com.fasterxml.jackson.databind.JsonNode
import ru.nsu.annotation.Persistable
import ru.nsu.exception.DeserializationException
import ru.nsu.exception.UnsupportedTypeException
import java.lang.reflect.Array
import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.math.BigDecimal

internal class JsonValueDecoder {

    fun decode(node: JsonNode, type: Type): Any? {
        if (node.isNull) {
            return null
        }

        val raw = TypeUtils.rawClass(type)
        return when {
            raw == Any::class.java -> decodeAny(node)
            raw == String::class.java -> node.asText()
            raw == Char::class.java || raw == java.lang.Character::class.java -> decodeChar(node)
            raw == Boolean::class.javaPrimitiveType || raw == java.lang.Boolean::class.java -> node.asBoolean()
            raw == Byte::class.javaPrimitiveType || raw == java.lang.Byte::class.java -> node.numberValue().toByte()
            raw == Short::class.javaPrimitiveType || raw == java.lang.Short::class.java -> node.numberValue().toShort()
            raw == Int::class.javaPrimitiveType || raw == java.lang.Integer::class.java -> node.numberValue().toInt()
            raw == Long::class.javaPrimitiveType || raw == java.lang.Long::class.java -> node.numberValue().toLong()
            raw == Float::class.javaPrimitiveType || raw == java.lang.Float::class.java -> node.numberValue().toFloat()
            raw == Double::class.javaPrimitiveType || raw == java.lang.Double::class.java -> node.numberValue().toDouble()
            raw == BigDecimal::class.java -> node.decimalValue()
            raw.isEnum -> decodeEnum(raw, node)
            raw.isArray || type is GenericArrayType -> decodeArray(node, type)
            Collection::class.java.isAssignableFrom(raw) -> decodeCollection(node, type, raw)
            Map::class.java.isAssignableFrom(raw) -> decodeMap(node, type)
            raw.isAnnotationPresent(Persistable::class.java) -> decodeObject(node, raw)
            else -> throw UnsupportedTypeException(type.typeName)
        }
    }

    private fun decodeChar(node: JsonNode): Char {
        val text = node.asText()
        if (text.length != 1) {
            throw DeserializationException("Expected single-character JSON string, but got '$text'")
        }
        return text.single()
    }

    private fun decodeArray(node: JsonNode, type: Type): Any {
        if (!node.isArray) {
            throw DeserializationException("Expected JSON array for type ${type.typeName}")
        }

        val componentType = when (type) {
            is Class<*> -> type.componentType
            is GenericArrayType -> type.genericComponentType
            else -> throw UnsupportedTypeException(type.typeName, "Unable to infer array component type")
        }

        val componentClass = TypeUtils.rawClass(componentType)
        val result = Array.newInstance(componentClass, node.size())
        node.forEachIndexed { index, item ->
            Array.set(result, index, decode(item, componentType))
        }
        return result
    }

    private fun decodeCollection(node: JsonNode, type: Type, raw: Class<*>): Collection<Any?> {
        if (!node.isArray) {
            throw DeserializationException("Expected JSON array for collection type ${type.typeName}")
        }

        val elementType = if (type is ParameterizedType) {
            type.actualTypeArguments[0]
        } else {
            Any::class.java
        }

        val values = node.map { decode(it, elementType) }
        return when {
            Set::class.java.isAssignableFrom(raw) -> LinkedHashSet(values)
            else -> values.toMutableList()
        }
    }

    private fun decodeMap(node: JsonNode, type: Type): Map<Any, Any?> {
        if (!node.isObject) {
            throw DeserializationException("Expected JSON object for map type ${type.typeName}")
        }

        val keyType = if (type is ParameterizedType) type.actualTypeArguments[0] else String::class.java
        val keyClass = TypeUtils.rawClass(keyType)
        val valueType = if (type is ParameterizedType) type.actualTypeArguments[1] else Any::class.java

        val map = LinkedHashMap<Any, Any?>()
        node.fields().forEach { (key, valueNode) ->
            val parsedKey = TypeUtils.stringToKey(key, keyClass)
            map[parsedKey] = decode(valueNode, valueType)
        }
        return map
    }

    private fun decodeObject(node: JsonNode, clazz: Class<*>): Any {
        if (!node.isObject) {
            throw DeserializationException("Expected JSON object for class ${clazz.name}")
        }

        val meta = PersistClassIntrospector.getMeta(clazz)
        val valuesByField = mutableMapOf<String, Any?>()
        for (field in meta.fields) {
            val valueNode = node.get(field.jsonName) ?: continue
            valuesByField[field.fieldName] = decode(valueNode, field.genericType)
        }

        return ObjectInstantiator.instantiate(clazz, valuesByField)
    }

    private fun decodeAny(node: JsonNode): Any? = when {
        node.isNull -> null
        node.isTextual -> node.asText()
        node.isBoolean -> node.asBoolean()
        node.isInt -> node.intValue()
        node.isLong -> node.longValue()
        node.isFloat || node.isDouble || node.isBigDecimal -> node.decimalValue()
        node.isArray -> node.map { decodeAny(it) }
        node.isObject -> {
            val result = LinkedHashMap<String, Any?>()
            node.fields().forEach { (key, child) -> result[key] = decodeAny(child) }
            result
        }

        else -> node.toString()
    }

    private fun decodeEnum(enumClass: Class<*>, node: JsonNode): Any {
        val literal = node.asText()
        return try {
            @Suppress("UNCHECKED_CAST")
            java.lang.Enum.valueOf(enumClass as Class<out Enum<*>>, literal)
        } catch (ex: Exception) {
            throw DeserializationException(
                "Failed to decode enum '${enumClass.name}' from '$literal': ${ex.message}",
                ex
            )
        }
    }
}
