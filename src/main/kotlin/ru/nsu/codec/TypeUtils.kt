package ru.nsu.codec

import ru.nsu.exception.InvalidMapKeyTypeException
import ru.nsu.exception.UnsupportedTypeException
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
        else -> throw UnsupportedTypeException(type.typeName, "Unable to resolve raw class")
    }

    fun stringToKey(value: String, keyClass: Class<*>): Any = when (keyClass) {
        String::class.java -> value
        Int::class.javaObjectType, Int::class.javaPrimitiveType -> value.toInt()
        Long::class.javaObjectType, Long::class.javaPrimitiveType -> value.toLong()
        Short::class.javaObjectType, Short::class.javaPrimitiveType -> value.toShort()
        Byte::class.javaObjectType, Byte::class.javaPrimitiveType -> value.toByte()
        Double::class.javaObjectType, Double::class.javaPrimitiveType -> value.toDouble()
        Float::class.javaObjectType, Float::class.javaPrimitiveType -> value.toFloat()
        Boolean::class.javaObjectType, Boolean::class.javaPrimitiveType -> value.toBooleanStrict()
        Char::class.javaObjectType, Char::class.javaPrimitiveType -> {
            if (value.length != 1) {
                throw InvalidMapKeyTypeException("${keyClass.name} (cannot parse '$value' as single char)")
            }
            value.single()
        }

        BigDecimal::class.java -> value.toBigDecimal()
        else -> {
            if (keyClass.isEnum) {
                enumValue(keyClass, value)
            } else {
                throw InvalidMapKeyTypeException(keyClass.name)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun enumValue(enumClass: Class<*>, literal: String): Any {
        return java.lang.Enum.valueOf(enumClass as Class<out Enum<*>>, literal)
    }
}
