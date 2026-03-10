package ru.nsu.core

import ru.nsu.api.Serializer
import java.nio.file.Path

class JsonSerializer(
    private val codec: Codec = Codec()
) : Serializer {

    override fun serialize(value: Any?): String {
        return codec.writePretty(codec.toJsonNode(value))
    }

    override fun serialize(value: Any?, file: Path) {
        codec.writeToFile(codec.toJsonNode(value), file)
    }
}
