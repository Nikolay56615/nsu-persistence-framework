package ru.nsu.core

import ru.nsu.annotation.PersistIgnore
import ru.nsu.annotation.PersistName
import ru.nsu.annotation.Persistable
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.plusAssign
import kotlin.reflect.jvm.kotlinProperty

internal data class PersistFieldMeta(
    val fieldName: String,
    val jsonName: String,
    val type: Class<*>,
    val genericType: Type,
    val field: Field
)

internal data class PersistClassMeta(
    val clazz: Class<*>,
    val fields: List<PersistFieldMeta>,
    val byFieldName: Map<String, PersistFieldMeta>,
    val byJsonName: Map<String, PersistFieldMeta>
)

internal object PersistClassIntrospector {
    private val cache = ConcurrentHashMap<Class<*>, PersistClassMeta>()

    fun getMeta(clazz: Class<*>): PersistClassMeta = cache.computeIfAbsent(clazz) { inspect(it) }

    fun requirePersistable(clazz: Class<*>) {
        if (!clazz.isAnnotationPresent(Persistable::class.java)) {
            throw Exception(clazz.name)
        }
    }

    private fun inspect(clazz: Class<*>): PersistClassMeta {
        requirePersistable(clazz)

        val fields = allFields(clazz)
            .asSequence()
            .filterNot { Modifier.isStatic(it.modifiers) }
            .filterNot { isIgnored(it) }
            .map { field ->
                field.trySetAccessible()
                PersistFieldMeta(
                    fieldName = field.name,
                    jsonName = resolveJsonName(field),
                    type = field.type,
                    genericType = field.genericType,
                    field = field
                )
            }
            .toList()

        ensureUniqueJsonNames(clazz, fields)

        return PersistClassMeta(
            clazz = clazz,
            fields = fields,
            byFieldName = fields.associateBy { it.fieldName },
            byJsonName = fields.associateBy { it.jsonName }
        )
    }

    private fun allFields(clazz: Class<*>): List<Field> {
        val result = mutableListOf<Field>()
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            result += current.declaredFields
            current = current.superclass
        }
        return result
    }

    private fun isIgnored(field: Field): Boolean {
        if (field.isAnnotationPresent(PersistIgnore::class.java)) {
            return true
        }
        return field.kotlinProperty?.annotations?.any { it is PersistIgnore } == true
    }

    private fun resolveJsonName(field: Field): String {
        val fieldAlias = field.getAnnotation(PersistName::class.java)?.value
        if (!fieldAlias.isNullOrBlank()) {
            return fieldAlias
        }

        val propertyAlias = field.kotlinProperty
            ?.annotations
            ?.filterIsInstance<PersistName>()
            ?.firstOrNull()
            ?.value
        if (!propertyAlias.isNullOrBlank()) {
            return propertyAlias
        }

        return field.name
    }

    private fun ensureUniqueJsonNames(clazz: Class<*>, fields: List<PersistFieldMeta>) {
        val duplicates = fields
            .groupBy { it.jsonName }
            .filterValues { it.size > 1 }
            .keys

        if (duplicates.isNotEmpty()) {
            throw Exception(
                "Class '${clazz.name}' contains duplicate JSON field names: ${duplicates.joinToString(", ")}"
            )
        }
    }
}
