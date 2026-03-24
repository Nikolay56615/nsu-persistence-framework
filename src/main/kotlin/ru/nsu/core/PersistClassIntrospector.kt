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
    val jsonName: String,
    val since: Int,
    val until: Int
) {
    fun isActiveAt(version: Int): Boolean = version in since..until
}

internal data class PersistFieldMeta(
    val fieldName: String,
    val jsonName: String,
    val type: Class<*>,
    val genericType: Type,
    val config: PersistFieldConfig,
    val field: Field
) {
    fun isActiveAt(version: Int): Boolean = config.isActiveAt(version)
}

internal data class PersistClassMeta(
    val clazz: Class<*>,
    val version: Int,
    val fields: List<PersistFieldMeta>,
    val byFieldName: Map<String, PersistFieldMeta>,
    val byJsonName: Map<String, PersistFieldMeta>
) {
    fun supportsVersion(targetVersion: Int): Boolean = targetVersion in 1..version

    fun fieldsForVersion(targetVersion: Int): List<PersistFieldMeta> = fields.filter { it.isActiveAt(targetVersion) }
}

internal object PersistClassIntrospector {
    private val reservedJsonNames = setOf(Codec.ID_FIELD, Codec.REF_FIELD, Codec.VERSION_FIELD)
    private val cache = ConcurrentHashMap<Class<*>, PersistClassMeta>()

    fun getMeta(clazz: Class<*>): PersistClassMeta = cache.computeIfAbsent(clazz) { inspect(it) }

    fun requirePersistable(clazz: Class<*>) {
        if (!clazz.isAnnotationPresent(Persistable::class.java)) {
            throw MissingPersistableAnnotationException(clazz.name)
        }
    }

    private fun inspect(clazz: Class<*>): PersistClassMeta {
        requirePersistable(clazz)
        val classVersion = clazz.getAnnotation(Persistable::class.java).version
        ensureValidClassVersion(clazz, classVersion)

        val fields = allFields(clazz)
            .asSequence()
            .filterNot { Modifier.isStatic(it.modifiers) }
            .filterNot { Modifier.isTransient(it.modifiers) }
            .filterNot { it.isSynthetic }
            .filterNot { it.name.endsWith("\$delegate") }
            .mapNotNull { field ->
                val config = resolveConfig(field, classVersion) ?: return@mapNotNull null
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
        ensureValidFieldVersions(clazz, classVersion, fields)
        ensureUniqueJsonNames(clazz, fields)
        ensureRequiredConstructorParametersPersisted(clazz, classVersion, fields)

        return PersistClassMeta(
            clazz = clazz,
            version = classVersion,
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

    private fun resolveConfig(field: Field, classVersion: Int): PersistFieldConfig? {
        val annotation = field.getAnnotation(PersistField::class.java)
            ?: field.kotlinProperty
                ?.annotations
                ?.filterIsInstance<PersistField>()
                ?.firstOrNull()
            ?: return null

        return PersistFieldConfig(
            jsonName = annotation.name.ifBlank { field.name },
            since = annotation.since,
            until = if (annotation.until == Int.MAX_VALUE) classVersion else annotation.until
        )
    }

    private fun ensureValidClassVersion(clazz: Class<*>, classVersion: Int) {
        if (classVersion < 1) {
            throw PersistenceException("Class '${clazz.name}' must declare @Persistable(version >= 1)")
        }
    }

    private fun ensureHasPersistedFields(clazz: Class<*>, fields: List<PersistFieldMeta>) {
        if (fields.isEmpty()) {
            throw PersistenceException("Class '${clazz.name}' must declare at least one @PersistField")
        }
    }

    private fun ensureValidFieldVersions(clazz: Class<*>, classVersion: Int, fields: List<PersistFieldMeta>) {
        fields.forEach { field ->
            if (field.config.since < 1) {
                throw PersistenceException(
                    "Field '${field.fieldName}' in class '${clazz.name}' must declare since >= 1"
                )
            }
            if (field.config.until < field.config.since) {
                throw PersistenceException(
                    "Field '${field.fieldName}' in class '${clazz.name}' must declare until >= since"
                )
            }
            if (field.config.until > classVersion) {
                throw PersistenceException(
                    "Field '${field.fieldName}' in class '${clazz.name}' cannot declare until > class version $classVersion"
                )
            }
        }
    }

    private fun ensureRequiredConstructorParametersPersisted(
        clazz: Class<*>,
        classVersion: Int,
        fields: List<PersistFieldMeta>
    ) {
        val primaryConstructor = clazz.kotlin.primaryConstructor ?: return
        val persistedFieldsByName = fields.associateBy { it.fieldName }

        primaryConstructor.parameters
            .asSequence()
            .filter { it.kind == KParameter.Kind.VALUE }
            .filter { !it.isOptional }
            .filter { !it.type.isMarkedNullable }
            .forEach { parameter ->
                val parameterName = parameter.name ?: return@forEach
                val field = persistedFieldsByName[parameterName]
                if (field == null || field.config.since != 1 || field.config.until != classVersion) {
                    throw PersistenceException(
                        "Required constructor parameter '$parameterName' in class '${clazz.name}' must be annotated with @PersistField and active for all supported versions"
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
