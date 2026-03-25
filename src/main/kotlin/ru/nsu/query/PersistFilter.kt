package ru.nsu.query

import com.fasterxml.jackson.databind.JsonNode

fun interface PersistFilter {
    fun matches(node: JsonNode): Boolean
}
