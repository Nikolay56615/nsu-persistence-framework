package ru.nsu.api

import kotlin.reflect.KClass

interface Deserializer<T : Any> {
    fun instance(): T
    fun collection(): List<T>
    fun map(keyClass: KClass<*>): Map<Any, T>
}