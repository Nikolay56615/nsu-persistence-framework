package ru.nsu.api

import java.nio.file.Path

interface Serializer {
    fun serialize(value: Any?): String
    fun serialize(value: Any?, file: Path)
}