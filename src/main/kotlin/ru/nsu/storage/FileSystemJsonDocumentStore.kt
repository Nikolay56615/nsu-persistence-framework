package ru.nsu.storage

import com.fasterxml.jackson.databind.JsonNode
import ru.nsu.codec.DocumentCodec
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import kotlin.io.path.extension
import kotlin.reflect.KClass

class FileSystemJsonDocumentStore(
    private var baseDirectory: Path = Paths.get(System.getProperty("user.dir")),
    private val codec: DocumentCodec
) : JsonDocumentStore {

    override fun setDirectory(path: Path) {
        baseDirectory = path
    }

    override fun readAll(clazz: KClass<*>): List<JsonNode> {
        val classDirectory = classDirectory(clazz)
        if (!Files.exists(classDirectory)) {
            return emptyList()
        }

        return Files.list(classDirectory)
            .use { stream ->
                stream
                    .filter { Files.isRegularFile(it) && it.extension.equals("json", ignoreCase = true) }
                    .sorted()
                    .map(codec::parse)
                    .toList()
            }
    }

    override fun write(clazz: KClass<*>, documentId: UUID, node: JsonNode) {
        val target = classDirectory(clazz).resolve("$documentId.json")
        codec.writeToFile(node, target)
    }

    override fun deleteMatching(clazz: KClass<*>, predicate: (JsonNode) -> Boolean) {
        val classDirectory = classDirectory(clazz)
        if (!Files.exists(classDirectory)) {
            return
        }

        Files.list(classDirectory)
            .use { stream ->
                stream
                    .filter { Files.isRegularFile(it) && it.extension.equals("json", ignoreCase = true) }
                    .sorted()
                    .forEach { file ->
                        val node = codec.parse(file)
                        if (predicate(node)) {
                            Files.deleteIfExists(file)
                        }
                    }
            }
    }

    private fun classDirectory(clazz: KClass<*>): Path {
        return baseDirectory
            .resolve("kpersist")
            .resolve(clazz.java.name)
    }
}
