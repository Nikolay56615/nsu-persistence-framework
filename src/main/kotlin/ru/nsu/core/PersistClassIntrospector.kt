package ru.nsu.core

import ru.nsu.annotation.PersistField
import ru.nsu.annotation.Persistable
import ru.nsu.exception.MissingPersistableAnnotationException
import ru.nsu.exception.PersistenceException
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.kotlinProperty

internal data class PersistFieldConfig(
    val jsonName: String
)

internal data class PersistFieldMeta(
    val fieldName: String,
    val jsonName: String,
    val type: Class<*>,
    val genericType: Type,
    val config: PersistFieldConfig,
    val field: Field
)

internal data class PersistClassMeta(
    val clazz: Class<*>,
    val fields: List<PersistFieldMeta>,
    val byFieldName: Map<String, PersistFieldMeta>,
    val byJsonName: Map<String, PersistFieldMeta>
)

internal object PersistClassIntrospector {
    private val reservedJsonNames = setOf(Codec.ID_FIELD, Codec.REF_FIELD)
    private val cache = ConcurrentHashMap<Class<*>, PersistClassMeta>()

    fun getMeta(clazz: Class<*>): PersistClassMeta = cache.computeIfAbsent(clazz) { inspect(it) }

    fun requirePersistable(clazz: Class<*>) {
        if (!clazz.isAnnotationPresent(Persistable::class.java)) {
            throw MissingPersistableAnnotationException(clazz.name)
        }
    }

    private fun inspect(clazz: Class<*>): PersistClassMeta {
        requirePersistable(clazz)

        val fields = allFields(clazz)
            .asSequence()
            .filterNot { Modifier.isStatic(it.modifiers) }
            .filterNot { Modifier.isTransient(it.modifiers) }
            .filterNot { it.isSynthetic }
            .filterNot { it.name.endsWith("\$delegate") }
            .mapNotNull { field ->
                val config = resolveConfig(field) ?: return@mapNotNull null
                field.trySetAccessible()
                PersistFieldMeta(
                    fieldName = field.name,
                    jsonName = config.jsonName,
                    type = field.type,
                    genericType = field.genericType,
                    config = config,
                    field = field
                )
            }
            .toList()

        ensureHasPersistedFields(clazz, fields)
        ensureUniqueJsonNames(clazz, fields)
        ensureRequiredConstructorParametersPersisted(clazz, fields)

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

    private fun resolveConfig(field: Field): PersistFieldConfig? {
        val annotation = field.getAnnotation(PersistField::class.java)
            ?: field.kotlinProperty
                ?.annotations
                ?.filterIsInstance<PersistField>()
                ?.firstOrNull()
            ?: return null

        return PersistFieldConfig(
            jsonName = annotation.name.ifBlank { field.name }
        )
    }

    private fun ensureHasPersistedFields(clazz: Class<*>, fields: List<PersistFieldMeta>) {
        if (fields.isEmpty()) {
            throw PersistenceException("Class '${clazz.name}' must declare at least one @PersistField")
        }
    }

    private fun ensureRequiredConstructorParametersPersisted(clazz: Class<*>, fields: List<PersistFieldMeta>) {
        val primaryConstructor = clazz.kotlin.primaryConstructor ?: return
        val persistedFieldNames = fields.mapTo(mutableSetOf()) { it.fieldName }

        primaryConstructor.parameters
            .asSequence()
            .filter { it.kind == KParameter.Kind.VALUE }
            .filter { !it.isOptional }
            .filter { !it.type.isMarkedNullable }
            .forEach { parameter ->
                val parameterName = parameter.name ?: return@forEach
                if (parameterName !in persistedFieldNames) {
                    throw PersistenceException(
                        "Required constructor parameter '$parameterName' in class '${clazz.name}' must be annotated with @PersistField"
                    )
                }
            }
    }

    private fun ensureUniqueJsonNames(clazz: Class<*>, fields: List<PersistFieldMeta>) {
        val reservedNames = fields
            .map { it.jsonName }
            .filter { it in reservedJsonNames }
            .distinct()

        if (reservedNames.isNotEmpty()) {
            throw PersistenceException(
                "Class '${clazz.name}' uses reserved JSON field names: ${reservedNames.joinToString(", ")}"
            )
        }

        val duplicates = fields
            .groupBy { it.jsonName }
            .filterValues { it.size > 1 }
            .keys

        if (duplicates.isNotEmpty()) {
            throw PersistenceException(
                "Class '${clazz.name}' contains duplicate JSON field names: ${duplicates.joinToString(", ")}"
            )
        }
    }
}
