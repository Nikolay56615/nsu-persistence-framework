package ru.nsu.core

import java.lang.reflect.Array
import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.math.BigDecimal

internal object TypeUtils {
    fun rawClass(type: Type): Class<*> = when (type) {
        is Class<*> -> type
        is ParameterizedType -> rawClass(type.rawType)
        is GenericArrayType -> {
            val component = rawClass(type.genericComponentType)
            Array.newInstance(component, 0).javaClass
        }

        is WildcardType -> rawClass(type.upperBounds.firstOrNull() ?: Any::class.java)
        is TypeVariable<*> -> rawClass(type.bounds.firstOrNull() ?: Any::class.java)
        else -> throw Exception("${type.typeName} Unable to resolve raw class")
    }

    fun parameterType(type: Type, index: Int): Type {
        if (type !is ParameterizedType) {
            throw Exception("${type.typeName} Expected parameterized type")
        }
        if (index !in type.actualTypeArguments.indices) {
            throw Exception("${type.typeName} Missing generic argument at index $index")
        }
        return type.actualTypeArguments[index]
    }

    fun isPrimitiveOrWrapper(clazz: Class<*>): Boolean = clazz.isPrimitive || clazz in setOf(
        java.lang.Boolean::class.java,
        java.lang.Byte::class.java,
        java.lang.Short::class.java,
        Integer::class.java,
        java.lang.Long::class.java,
        java.lang.Float::class.java,
        java.lang.Double::class.java,
        Character::class.java
    )

    fun stringToKey(value: String, keyClass: Class<*>): Any = when (keyClass) {
        String::class.java -> value
        Integer::class.java, Int::class.javaPrimitiveType -> value.toInt()
        java.lang.Long::class.java, Long::class.javaPrimitiveType -> value.toLong()
        java.lang.Short::class.java, Short::class.javaPrimitiveType -> value.toShort()
        java.lang.Byte::class.java, Byte::class.javaPrimitiveType -> value.toByte()
        java.lang.Double::class.java, Double::class.javaPrimitiveType -> value.toDouble()
        java.lang.Float::class.java, Float::class.javaPrimitiveType -> value.toFloat()
        java.lang.Boolean::class.java, Boolean::class.javaPrimitiveType -> value.toBooleanStrict()
        Character::class.java, Char::class.javaPrimitiveType -> {
            if (value.length != 1) {
                throw Exception("${keyClass.name} (cannot parse '$value' as single char)")
            }
            value.single()
        }

        BigDecimal::class.java -> value.toBigDecimal()
        else -> {
            if (keyClass.isEnum) {
                enumValue(keyClass, value)
            } else {
                throw Exception(keyClass.name)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun enumValue(enumClass: Class<*>, literal: String): Any {
        return java.lang.Enum.valueOf(enumClass as Class<out Enum<*>>, literal)
    }
}