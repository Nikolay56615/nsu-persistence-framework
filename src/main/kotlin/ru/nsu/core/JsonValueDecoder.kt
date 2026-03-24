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
    private class DeserializationContext(
        private val expectedRootVersion: Int?
    ) {
        val objectsById = mutableMapOf<String, Any>()
        private var rootObjectPending = true

        fun resolveReference(referenceId: String): Any {
            return objectsById[referenceId] ?: throw DeserializationException(
                "Unresolved object reference '$referenceId'. " +
                    "The target object has not been created yet; cyclic graphs require the target class " +
                    "to support pre-creation via an accessible no-arg constructor and mutable fields."
            )
        }

        fun validateRootVersion(actualVersion: Int, clazz: Class<*>) {
            if (!rootObjectPending) {
                return
            }
            rootObjectPending = false

            val expected = expectedRootVersion ?: return
            if (expected < 1) {
                throw DeserializationException("Expected document version must be >= 1 for ${clazz.name}")
            }
            if (actualVersion != expected) {
                throw DeserializationException(
                    "Expected document version $expected for ${clazz.name}, but got $actualVersion"
                )
            }
        }
    }

    fun decode(node: JsonNode, type: Type): Any? = decode(node, type, DeserializationContext(null))

    fun decodeRoot(node: JsonNode, type: Type, expectedVersion: Int? = null): Any? {
        return decode(node, type, DeserializationContext(expectedVersion))
    }

    private fun decode(node: JsonNode, type: Type, context: DeserializationContext): Any? {
        if (node.isNull) {
            return null
        }
        if (node.isObject && node.has(Codec.REF_FIELD)) {
            val referenceId = node.get(Codec.REF_FIELD)?.asText()
                ?: throw DeserializationException("Reference node must contain a non-null ${Codec.REF_FIELD} value")
            return context.resolveReference(referenceId)
        }

        val raw = TypeUtils.rawClass(type)
        return when {
            raw == Any::class.java -> decodeAny(node, context)
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
            raw.isArray || type is GenericArrayType -> decodeArray(node, type, context)
            Collection::class.java.isAssignableFrom(raw) -> decodeCollection(node, type, raw, context)
            Map::class.java.isAssignableFrom(raw) -> decodeMap(node, type, context)
            raw.isAnnotationPresent(Persistable::class.java) -> decodeObject(node, raw, context)
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

    private fun decodeArray(node: JsonNode, type: Type, context: DeserializationContext): Any {
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
            Array.set(result, index, decode(item, componentType, context))
        }
        return result
    }

    private fun decodeCollection(
        node: JsonNode,
        type: Type,
        raw: Class<*>,
        context: DeserializationContext
    ): Collection<Any?> {
        if (!node.isArray) {
            throw DeserializationException("Expected JSON array for collection type ${type.typeName}")
        }

        val elementType = if (type is ParameterizedType) {
            type.actualTypeArguments[0]
        } else {
            Any::class.java
        }

        val values = node.map { decode(it, elementType, context) }
        return when {
            Set::class.java.isAssignableFrom(raw) -> LinkedHashSet(values)
            else -> values.toMutableList()
        }
    }

    private fun decodeMap(node: JsonNode, type: Type, context: DeserializationContext): Map<Any, Any?> {
        if (!node.isObject) {
            throw DeserializationException("Expected JSON object for map type ${type.typeName}")
        }

        val keyType = if (type is ParameterizedType) type.actualTypeArguments[0] else String::class.java
        val keyClass = TypeUtils.rawClass(keyType)
        val valueType = if (type is ParameterizedType) type.actualTypeArguments[1] else Any::class.java

        val map = LinkedHashMap<Any, Any?>()
        node.fields().forEach { (key, valueNode) ->
            val parsedKey = TypeUtils.stringToKey(key, keyClass)
            map[parsedKey] = decode(valueNode, valueType, context)
        }
        return map
    }

    private fun decodeObject(node: JsonNode, clazz: Class<*>, context: DeserializationContext): Any {
        if (!node.isObject) {
            throw DeserializationException("Expected JSON object for class ${clazz.name}")
        }

        val meta = PersistClassIntrospector.getMeta(clazz)
        val documentVersion = resolveDocumentVersion(node, meta)
        context.validateRootVersion(documentVersion, clazz)

        val objectId = node.get(Codec.ID_FIELD)?.asText()
        if (objectId != null && context.objectsById.containsKey(objectId)) {
            return context.objectsById.getValue(objectId)
        }

        if (objectId != null && ObjectInstantiator.canInstantiateWithoutData(clazz)) {
            val instance = ObjectInstantiator.instantiateEmpty(clazz)
            context.objectsById[objectId] = instance

            val valuesByField = decodeFieldValues(node, meta, context, documentVersion)
            ObjectInstantiator.populateFields(instance, clazz, valuesByField)
            return instance
        }

        return try {
            val valuesByField = decodeFieldValues(node, meta, context, documentVersion)
            val instance = ObjectInstantiator.instantiate(clazz, valuesByField)
            if (objectId != null) {
                context.objectsById[objectId] = instance
            }
            instance
        } catch (ex: DeserializationException) {
            if (objectId != null && !ObjectInstantiator.canInstantiateWithoutData(clazz)) {
                throw DeserializationException(
                    "Failed to restore object graph for ${clazz.name}. " +
                        "This object participates in a \$id/\$ref graph but cannot be pre-created before its fields " +
                        "are decoded. Add an accessible no-arg constructor and mutable fields, or remove the cycle.",
                    ex
                )
            }
            throw ex
        }
    }

    private fun decodeFieldValues(
        node: JsonNode,
        meta: PersistClassMeta,
        context: DeserializationContext,
        documentVersion: Int
    ): Map<String, Any?> {
        val valuesByField = mutableMapOf<String, Any?>()
        for (field in meta.fieldsForVersion(documentVersion)) {
            val valueNode = node.get(field.jsonName) ?: continue
            valuesByField[field.fieldName] = decode(valueNode, field.genericType, context)
        }
        return valuesByField
    }

    private fun resolveDocumentVersion(node: JsonNode, meta: PersistClassMeta): Int {
        val versionNode = node.get(Codec.VERSION_FIELD)
        val documentVersion = when {
            versionNode == null || versionNode.isNull -> 1
            !versionNode.canConvertToInt() -> throw DeserializationException(
                "Document version field '${Codec.VERSION_FIELD}' must be an integer for ${meta.clazz.name}"
            )

            else -> versionNode.intValue()
        }

        if (!meta.supportsVersion(documentVersion)) {
            throw DeserializationException(
                "Document version $documentVersion is not supported by ${meta.clazz.name}; supported range is 1..${meta.version}"
            )
        }

        return documentVersion
    }

    private fun decodeAny(node: JsonNode, context: DeserializationContext): Any? = when {
        node.isNull -> null
        node.isTextual -> node.asText()
        node.isBoolean -> node.asBoolean()
        node.isInt -> node.intValue()
        node.isLong -> node.longValue()
        node.isFloat || node.isDouble || node.isBigDecimal -> node.decimalValue()
        node.isArray -> node.map { decodeAny(it, context) }
        node.isObject -> {
            val result = LinkedHashMap<String, Any?>()
            node.fields().forEach { (key, child) -> result[key] = decodeAny(child, context) }
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
