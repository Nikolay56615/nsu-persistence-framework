package ru.nsu.codec

import ru.nsu.exception.DeserializationException
import ru.nsu.exception.MissingNoArgConstructorException
import ru.nsu.metadata.PersistMetadataResolver
import java.lang.reflect.Constructor
import java.lang.reflect.Parameter
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

internal class ObjectInstantiator(
    private val metadataResolver: PersistMetadataResolver
) {

    fun instantiate(clazz: Class<*>, valuesByField: Map<String, Any?>): Any {
        instantiateWithKotlinConstructor(clazz, valuesByField)?.let { return it }

        return when (val javaResolution = instantiateWithJavaConstructor(clazz, valuesByField)) {
            is JavaConstructorResolution.Success -> javaResolution.instance
            is JavaConstructorResolution.Unsupported -> {
                if (findNoArgConstructor(clazz) == null) {
                    throw DeserializationException(javaResolution.message, javaResolution.cause)
                }
                instantiateWithNoArgConstructor(clazz, valuesByField)
            }

            JavaConstructorResolution.NotApplicable -> instantiateWithNoArgConstructor(clazz, valuesByField)
        }
    }

    fun canInstantiateWithoutData(clazz: Class<*>): Boolean = findNoArgConstructor(clazz) != null

    fun instantiateEmpty(clazz: Class<*>): Any {
        val constructor = findNoArgConstructor(clazz)
            ?: throw DeserializationException(
                "Class ${clazz.name} cannot be pre-created for cyclic reference restoration. " +
                    "Add an accessible no-arg constructor and mutable fields, or remove the cycle."
            )
        constructor.trySetAccessible()

        return try {
            constructor.newInstance()
        } catch (ex: Exception) {
            throw DeserializationException("Failed to instantiate ${clazz.name}: ${ex.message}", ex)
        }
    }

    fun populateFields(
        instance: Any,
        clazz: Class<*>,
        valuesByField: Map<String, Any?>,
        skipFields: Set<String> = emptySet()
    ) {
        setRemainingFields(instance, clazz, valuesByField, skipFields)
    }

    private fun instantiateWithKotlinConstructor(clazz: Class<*>, valuesByField: Map<String, Any?>): Any? {
        val primary = clazz.kotlin.primaryConstructor ?: return null
        primary.isAccessible = true

        val ctorArgs = mutableMapOf<KParameter, Any?>()
        for (parameter in primary.parameters) {
            if (parameter.kind != KParameter.Kind.VALUE) {
                continue
            }

            val name = parameter.name
            if (name != null && valuesByField.containsKey(name)) {
                ctorArgs[parameter] = valuesByField[name]
                continue
            }

            if (!parameter.isOptional && !parameter.type.isMarkedNullable) {
                throw DeserializationException("Missing required constructor argument '$name' for ${clazz.name}")
            }
        }

        val instance = try {
            primary.callBy(ctorArgs)
        } catch (ex: Exception) {
            throw DeserializationException("Failed to instantiate ${clazz.name}: ${ex.message}", ex)
        }

        val constructorFieldNames = constructorFieldNames(primary)
        setRemainingFields(instance, clazz, valuesByField, constructorFieldNames)
        return instance
    }

    private fun constructorFieldNames(primary: KFunction<*>): Set<String> {
        return primary.parameters
            .asSequence()
            .filter { it.kind == KParameter.Kind.VALUE }
            .mapNotNull { it.name }
            .toSet()
    }

    private fun instantiateWithJavaConstructor(clazz: Class<*>, valuesByField: Map<String, Any?>): JavaConstructorResolution {
        val persistedFieldNames = metadataResolver.getMeta(clazz).byFieldName.keys
        val candidates = clazz.declaredConstructors
            .asSequence()
            .filterNot { it.parameterCount == 0 }
            .filterNot { it.isSynthetic }
            .sortedByDescending { it.parameterCount }
            .toList()

        if (candidates.isEmpty()) {
            return JavaConstructorResolution.NotApplicable
        }

        var sawConstructorWithoutNames = false
        var sawUnsupportedParameterMapping = false
        var sawPrimitiveGap = false
        var lastFailure: DeserializationException? = null

        for (constructor in candidates) {
            val parameterNames = constructorParameters(constructor)
            if (parameterNames == null) {
                sawConstructorWithoutNames = true
                continue
            }

            if (parameterNames.any { it !in persistedFieldNames }) {
                sawUnsupportedParameterMapping = true
                continue
            }

            val invocation = prepareJavaConstructorArgs(constructor, parameterNames, valuesByField)
            if (invocation == null) {
                sawPrimitiveGap = true
                continue
            }

            constructor.trySetAccessible()

            val instance = try {
                constructor.newInstance(*invocation.arguments)
            } catch (ex: Exception) {
                lastFailure = DeserializationException("Failed to instantiate ${clazz.name}: ${ex.message}", ex)
                continue
            }

            setRemainingFields(instance, clazz, valuesByField, invocation.boundFieldNames)
            return JavaConstructorResolution.Success(instance)
        }

        if (lastFailure != null) {
            return JavaConstructorResolution.Unsupported(
                lastFailure.message ?: "Failed to instantiate ${clazz.name}",
                lastFailure
            )
        }

        if (sawUnsupportedParameterMapping || sawPrimitiveGap || sawConstructorWithoutNames) {
            return JavaConstructorResolution.Unsupported(
                buildString {
                    append("Failed to instantiate ${clazz.name}: no supported Java constructor found. ")
                    append("Constructor-based Java deserialization requires parameter names retained at compile time and ")
                    append("every constructor parameter to match an @PersistField field. ")
                    if (sawPrimitiveGap) {
                        append("Version-gated primitive constructor fields require a no-arg constructor or boxed type. ")
                    }
                    append("Otherwise provide an accessible no-arg constructor.")
                }
            )
        }

        return JavaConstructorResolution.NotApplicable
    }

    private fun constructorParameters(constructor: Constructor<*>): List<String>? {
        if (constructor.parameters.any { !it.isNamePresent }) {
            return null
        }
        return constructor.parameters.map(Parameter::getName)
    }

    private fun prepareJavaConstructorArgs(
        constructor: Constructor<*>,
        parameterNames: List<String>,
        valuesByField: Map<String, Any?>
    ): JavaConstructorInvocation? {
        val arguments = arrayOfNulls<Any?>(constructor.parameterCount)
        val boundFieldNames = linkedSetOf<String>()

        for ((index, parameter) in constructor.parameters.withIndex()) {
            val parameterName = parameterNames[index]
            if (valuesByField.containsKey(parameterName)) {
                arguments[index] = valuesByField[parameterName]
                boundFieldNames += parameterName
                continue
            }

            if (parameter.type.isPrimitive) {
                return null
            }

            arguments[index] = null
        }

        if (boundFieldNames.isEmpty()) {
            return null
        }

        return JavaConstructorInvocation(arguments, boundFieldNames)
    }

    private fun instantiateWithNoArgConstructor(clazz: Class<*>, valuesByField: Map<String, Any?>): Any {
        val constructor = findNoArgConstructor(clazz) ?: throw MissingNoArgConstructorException(clazz.name)
        constructor.trySetAccessible()

        val instance = try {
            constructor.newInstance()
        } catch (ex: Exception) {
            throw DeserializationException("Failed to instantiate ${clazz.name}: ${ex.message}", ex)
        }

        setRemainingFields(instance, clazz, valuesByField, emptySet())
        return instance
    }

    private fun findNoArgConstructor(clazz: Class<*>): java.lang.reflect.Constructor<*>? {
        return try {
            clazz.getDeclaredConstructor()
        } catch (_: Exception) {
            null
        }
    }

    private fun setRemainingFields(
        instance: Any,
        clazz: Class<*>,
        valuesByField: Map<String, Any?>,
        skipFields: Set<String>
    ) {
        val meta = metadataResolver.getMeta(clazz)
        for (field in meta.fields) {
            if (field.fieldName in skipFields || !valuesByField.containsKey(field.fieldName)) {
                continue
            }

            try {
                field.field.set(instance, valuesByField[field.fieldName])
            } catch (ex: Exception) {
                throw DeserializationException(
                    "Failed to set field '${field.fieldName}' on ${clazz.name}: ${ex.message}",
                    ex
                )
            }
        }
    }

    private data class JavaConstructorInvocation(
        val arguments: Array<Any?>,
        val boundFieldNames: Set<String>
    )

    private sealed interface JavaConstructorResolution {
        data object NotApplicable : JavaConstructorResolution
        data class Success(val instance: Any) : JavaConstructorResolution
        data class Unsupported(val message: String, val cause: Throwable? = null) : JavaConstructorResolution
    }
}
