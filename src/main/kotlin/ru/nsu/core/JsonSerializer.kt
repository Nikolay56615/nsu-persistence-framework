package ru.nsu.core

import ru.nsu.api.Serializer
import java.nio.file.Path

class JsonSerializer(
    private val codec: Codec = Codec()
) : Serializer {

    override fun serialize(value: Any?): String {
        return codec.writePretty(codec.toJsonNode(value))
    }

    override fun serialize(value: Any?, version: Int): String {
        return codec.writePretty(codec.toJsonNode(value, version))
    }

    override fun serialize(value: Any?, file: Path) {
        codec.writeToFile(codec.toJsonNode(value), file)
    }

    override fun serialize(value: Any?, file: Path, version: Int) {
        codec.writeToFile(codec.toJsonNode(value, version), file)
    }
}
