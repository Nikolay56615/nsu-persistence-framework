package ru.nsu

fun interface PersistFilter {
    fun matches(node: Any): Boolean //TODO: Provide actual json node
}