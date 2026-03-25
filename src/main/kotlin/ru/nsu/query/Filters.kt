package ru.nsu.query

import com.fasterxml.jackson.databind.JsonNode
import ru.nsu.exception.InvalidFilterException
import java.math.BigDecimal

object Filters {
    fun eq(path: String, expected: Any?): PersistFilter = ComparisonFilter(path, ComparisonOperator.EQ, expected)

    fun ne(path: String, expected: Any?): PersistFilter = ComparisonFilter(path, ComparisonOperator.NE, expected)

    fun gt(path: String, expected: Any): PersistFilter = ComparisonFilter(path, ComparisonOperator.GT, expected)

    fun gte(path: String, expected: Any): PersistFilter = ComparisonFilter(path, ComparisonOperator.GTE, expected)

    fun lt(path: String, expected: Any): PersistFilter = ComparisonFilter(path, ComparisonOperator.LT, expected)

    fun lte(path: String, expected: Any): PersistFilter = ComparisonFilter(path, ComparisonOperator.LTE, expected)

    fun and(vararg filters: PersistFilter): PersistFilter {
        if (filters.isEmpty()) {
            throw InvalidFilterException("AND filter requires at least one nested filter")
        }
        return PersistFilter { node -> filters.all { it.matches(node) } }
    }

    fun or(vararg filters: PersistFilter): PersistFilter {
        if (filters.isEmpty()) {
            throw InvalidFilterException("OR filter requires at least one nested filter")
        }
        return PersistFilter { node -> filters.any { it.matches(node) } }
    }

    fun not(filter: PersistFilter): PersistFilter = PersistFilter { node -> !filter.matches(node) }
}

private enum class ComparisonOperator {
    EQ,
    NE,
    GT,
    GTE,
    LT,
    LTE
}

private class ComparisonFilter(
    path: String,
    private val operator: ComparisonOperator,
    expected: Any?
) : PersistFilter {
    private val resolvedPath = path
    private val segments = validatePath(path)
    private val expectedValue = normalizeExpected(expected, path)

    override fun matches(node: JsonNode): Boolean {
        val valueNode = resolvePath(node, segments, resolvedPath)
        val actualValue = normalizeNode(valueNode, resolvedPath)

        return when (operator) {
            ComparisonOperator.EQ -> equalsValue(actualValue, expectedValue, operator, resolvedPath)
            ComparisonOperator.NE -> !equalsValue(actualValue, expectedValue, operator, resolvedPath)
            ComparisonOperator.GT -> compare(actualValue, expectedValue, operator, resolvedPath) > 0
            ComparisonOperator.GTE -> compare(actualValue, expectedValue, operator, resolvedPath) >= 0
            ComparisonOperator.LT -> compare(actualValue, expectedValue, operator, resolvedPath) < 0
            ComparisonOperator.LTE -> compare(actualValue, expectedValue, operator, resolvedPath) <= 0
        }
    }
}

private fun validatePath(path: String): List<String> {
    if (path.isBlank()) {
        throw InvalidFilterException("Field path cannot be blank")
    }

    val segments = path.split('.')
    if (segments.any { it.isBlank() }) {
        throw InvalidFilterException("Invalid path '$path': empty path segment is not allowed")
    }
    if (segments.any { '[' in it || ']' in it }) {
        throw InvalidFilterException("Invalid path '$path': array indexing is not supported")
    }

    return segments
}

private fun resolvePath(root: JsonNode, segments: List<String>, path: String): JsonNode? {
    var current: JsonNode? = root

    for ((index, segment) in segments.withIndex()) {
        if (current == null || current.isNull || current.isMissingNode) {
            return null
        }

        if (!current.isObject) {
            if (current.isArray) {
                throw InvalidFilterException("Cannot resolve path '$path': array traversal is not supported")
            }

            val traversedPath = segments.take(index).joinToString(".")
            throw InvalidFilterException(
                "Cannot resolve path '$path': '$traversedPath' does not reference an object value"
            )
        }

        current = current.get(segment)
    }

    return current
}

private fun normalizeExpected(value: Any?, path: String): Any? = when (value) {
    null -> null
    is String -> value
    is Char -> value.toString()
    is Boolean -> value
    is BigDecimal -> value
    is Byte, is Short, is Int, is Long, is Float, is Double -> value.toString().toBigDecimal()
    is Enum<*> -> value.name
    else -> throw InvalidFilterException(
        "Unsupported expected value type '${value::class.qualifiedName}' for filter path '$path'"
    )
}

private fun normalizeNode(node: JsonNode?, path: String): Any? {
    if (node == null || node.isNull || node.isMissingNode) {
        return null
    }

    return when {
        node.isNumber -> node.decimalValue()
        node.isTextual -> node.textValue()
        node.isBoolean -> node.booleanValue()
        node.isArray -> throw InvalidFilterException(
            "Cannot compare array value at path '$path': arrays are not supported in filters"
        )

        node.isObject -> throw InvalidFilterException(
            "Cannot compare object value at path '$path': objects are not supported in filters"
        )

        else -> throw InvalidFilterException("Unsupported JSON value at path '$path'")
    }
}

private fun equalsValue(actual: Any?, expected: Any?, operator: ComparisonOperator, path: String): Boolean {
    if (actual == null || expected == null) {
        return actual == expected
    }

    if (actual is BigDecimal && expected is BigDecimal) {
        return actual.compareTo(expected) == 0
    }

    if (actual::class != expected::class) {
        throw incompatibleTypes(path, operator, actual, expected)
    }

    return actual == expected
}

private fun compare(actual: Any?, expected: Any?, operator: ComparisonOperator, path: String): Int {
    if (actual == null || expected == null) {
        throw InvalidFilterException("Cannot apply $operator comparison to null at path '$path'")
    }

    if (actual is BigDecimal && expected is BigDecimal) {
        return actual.compareTo(expected)
    }

    if (actual is String && expected is String) {
        return actual.compareTo(expected)
    }

    throw incompatibleTypes(path, operator, actual, expected)
}

private fun incompatibleTypes(
    path: String,
    operator: ComparisonOperator,
    actual: Any?,
    expected: Any?
): InvalidFilterException {
    return InvalidFilterException(
        "Cannot compare values of types ${typeName(actual)} and ${typeName(expected)} with operator $operator at path '$path'"
    )
}

private fun typeName(value: Any?): String = value?.let { it::class.qualifiedName ?: it::class.simpleName ?: "unknown" } ?: "null"
