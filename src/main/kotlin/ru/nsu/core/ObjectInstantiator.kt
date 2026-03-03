package ru.nsu.core

import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

internal object ObjectInstantiator {

    fun instantiate(clazz: Class<*>, valuesByField: Map<String, Any?>): Any {
        return instantiateWithKotlinConstructor(clazz, valuesByField)
            ?: instantiateWithNoArgConstructor(clazz, valuesByField)
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
                throw RuntimeException("Missing required constructor argument '$name' for ${clazz.name}")
            }
        }

        val instance = try {
            primary.callBy(ctorArgs)
        } catch (ex: Exception) {
            throw RuntimeException("Failed to instantiate ${clazz.name}: ${ex.message}", ex)
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

    private fun instantiateWithNoArgConstructor(clazz: Class<*>, valuesByField: Map<String, Any?>): Any {
        val constructor = try {
            clazz.getDeclaredConstructor()
        } catch (ex: Exception) {
            throw RuntimeException(
                "Failed to instantiate ${clazz.name}: no suitable constructor found",
                ex
            )
        }
        constructor.trySetAccessible()

        val instance = try {
            constructor.newInstance()
        } catch (ex: Exception) {
            throw RuntimeException("Failed to instantiate ${clazz.name}: ${ex.message}", ex)
        }

        setRemainingFields(instance, clazz, valuesByField, emptySet())
        return instance
    }

    private fun setRemainingFields(
        instance: Any,
        clazz: Class<*>,
        valuesByField: Map<String, Any?>,
        skipFields: Set<String>
    ) {
        val meta = PersistClassIntrospector.getMeta(clazz)
        for (field in meta.fields) {
            if (field.fieldName in skipFields || !valuesByField.containsKey(field.fieldName)) {
                continue
            }

            try {
                field.field.set(instance, valuesByField[field.fieldName])
            } catch (ex: Exception) {
                throw RuntimeException(
                    "Failed to set field '${field.fieldName}' on ${clazz.name}: ${ex.message}",
                    ex
                )
            }
        }
    }
}
