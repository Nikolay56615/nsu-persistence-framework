package ru.nsu.core

import com.fasterxml.jackson.databind.JsonNode
import ru.nsu.PersistFilter
import ru.nsu.api.Session
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import kotlin.io.path.extension
import kotlin.reflect.KClass

class JsonSession(
    private var baseDirectory: Path = Paths.get(System.getProperty("user.dir")),
    private val codec: Codec
) : Session {

    private data class PendingInsert(val id: UUID, val node: JsonNode)

    private val pendingInserts = mutableMapOf<KClass<*>, MutableList<PendingInsert>>()
    private val pendingDeletes = mutableMapOf<KClass<*>, MutableList<PersistFilter>>()

    override fun setDirectory(path: Path): Session {
        baseDirectory = path
        return this
    }

    override fun insert(obj: Any): Session {
        val clazz = obj::class
        PersistClassIntrospector.requirePersistable(clazz.java)

        val node = codec.toJsonNode(obj)
        val bucket = pendingInserts.getOrPut(clazz) { mutableListOf() }
        bucket += PendingInsert(UUID.randomUUID(), node)
        return this
    }

    override fun <T : Any> find(clazz: KClass<T>): List<T> {
        return find(clazz) { true }
    }

    override fun <T : Any> find(clazz: KClass<T>, filter: PersistFilter): List<T> {
        PersistClassIntrospector.requirePersistable(clazz.java)

        return visibleNodes(clazz)
            .asSequence()
            .filter { filter.matches(it) }
            .map { codec.decodeToClass(it, clazz) }
            .toList()
    }

    override fun <T : Any> delete(clazz: KClass<T>, filter: PersistFilter): Session {
        PersistClassIntrospector.requirePersistable(clazz.java)

        val bucket = pendingDeletes.getOrPut(clazz) { mutableListOf() }
        bucket += filter
        return this
    }

    override fun persist() {
        val classes = (pendingInserts.keys + pendingDeletes.keys).toSet()

        classes.forEach { clazz ->
            val classDirectory = classDirectory(clazz)
            Files.createDirectories(classDirectory)

            val deleteFilters = pendingDeletes[clazz].orEmpty()
            if (deleteFilters.isNotEmpty()) {
                persistedFiles(clazz)
                    .forEach { file ->
                        val node = codec.parse(file)
                        if (deleteFilters.any { it.matches(node) }) {
                            Files.deleteIfExists(file)
                        }
                    }
            }

            val insertsToPersist = pendingInserts[clazz]
                .orEmpty()
                .filterNot { insert -> deleteFilters.any { it.matches(insert.node) } }

            insertsToPersist.forEach { insert ->
                val target = classDirectory.resolve("${insert.id}.json")
                codec.writeToFile(insert.node, target)
            }
        }

        pendingInserts.clear()
        pendingDeletes.clear()
    }

    private fun <T : Any> visibleNodes(clazz: KClass<T>): List<JsonNode> {
        val deleteFilters = pendingDeletes[clazz].orEmpty()
        val persistedNodes = persistedFiles(clazz).map(codec::parse)
        val pendingNodes = pendingInserts[clazz].orEmpty().map { it.node }

        return (persistedNodes + pendingNodes)
            .filterNot { node -> deleteFilters.any { it.matches(node) } }
    }

    private fun persistedFiles(clazz: KClass<*>): List<Path> {
        val classDirectory = classDirectory(clazz)
        if (!Files.exists(classDirectory)) {
            return emptyList()
        }

        return Files.list(classDirectory)
            .use { stream ->
                stream
                    .filter { Files.isRegularFile(it) && it.extension.equals("json", ignoreCase = true) }
                    .sorted()
                    .toList()
            }
    }

    private fun classDirectory(clazz: KClass<*>): Path {
        return baseDirectory
            .resolve("kpersist")
            .resolve(clazz.java.name)
    }
}
