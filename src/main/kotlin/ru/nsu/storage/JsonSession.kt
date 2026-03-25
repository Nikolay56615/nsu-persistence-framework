package ru.nsu.storage

import com.fasterxml.jackson.databind.JsonNode
import ru.nsu.api.Session
import ru.nsu.codec.DocumentCodec
import ru.nsu.codec.JsonCodec
import ru.nsu.metadata.ReflectionPersistMetadataResolver
import ru.nsu.query.PersistFilter
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import kotlin.reflect.KClass

class JsonSession(
    private var baseDirectory: Path = Paths.get(System.getProperty("user.dir")),
    private val codec: DocumentCodec = JsonCodec()
) : Session {

    private data class PendingInsert(val id: UUID, val node: JsonNode)

    private val pendingInserts = mutableMapOf<KClass<*>, MutableList<PendingInsert>>()
    private val pendingDeletes = mutableMapOf<KClass<*>, MutableList<PersistFilter>>()
    private val metadataResolver = ReflectionPersistMetadataResolver
    private val store: JsonDocumentStore = FileSystemJsonDocumentStore(baseDirectory, codec)

    override fun setDirectory(path: Path): Session {
        baseDirectory = path
        store.setDirectory(path)
        return this
    }

    override fun insert(obj: Any): Session {
        val clazz = obj::class
        metadataResolver.requirePersistable(clazz.java)

        val node = codec.toJsonNode(obj)
        val bucket = pendingInserts.getOrPut(clazz) { mutableListOf() }
        bucket += PendingInsert(UUID.randomUUID(), node)
        return this
    }

    override fun insert(obj: Any, version: Int): Session {
        val clazz = obj::class
        metadataResolver.requirePersistable(clazz.java)

        val node = codec.toJsonNode(obj, version)
        val bucket = pendingInserts.getOrPut(clazz) { mutableListOf() }
        bucket += PendingInsert(UUID.randomUUID(), node)
        return this
    }

    override fun <T : Any> find(clazz: KClass<T>): List<T> {
        return find(clazz) { true }
    }

    override fun <T : Any> find(clazz: KClass<T>, expectedVersion: Int): List<T> {
        return find(clazz, { true }, expectedVersion)
    }

    override fun <T : Any> find(clazz: KClass<T>, filter: PersistFilter): List<T> {
        metadataResolver.requirePersistable(clazz.java)

        return visibleNodes(clazz)
            .asSequence()
            .filter { filter.matches(it) }
            .map { codec.decodeToClass(it, clazz) }
            .toList()
    }

    override fun <T : Any> find(clazz: KClass<T>, filter: PersistFilter, expectedVersion: Int): List<T> {
        metadataResolver.requirePersistable(clazz.java)

        return visibleNodes(clazz)
            .asSequence()
            .filter { filter.matches(it) }
            .map { codec.decodeToClass(it, clazz, expectedVersion) }
            .toList()
    }

    override fun <T : Any> delete(clazz: KClass<T>, filter: PersistFilter): Session {
        metadataResolver.requirePersistable(clazz.java)

        val bucket = pendingDeletes.getOrPut(clazz) { mutableListOf() }
        bucket += filter
        return this
    }

    override fun persist() {
        val classes = (pendingInserts.keys + pendingDeletes.keys).toSet()

        classes.forEach { clazz ->
            val deleteFilters = pendingDeletes[clazz].orEmpty()
            if (deleteFilters.isNotEmpty()) {
                store.deleteMatching(clazz) { node -> deleteFilters.any { it.matches(node) } }
            }

            val insertsToPersist = pendingInserts[clazz]
                .orEmpty()
                .filterNot { insert -> deleteFilters.any { it.matches(insert.node) } }

            insertsToPersist.forEach { insert ->
                store.write(clazz, insert.id, insert.node)
            }
        }

        pendingInserts.clear()
        pendingDeletes.clear()
    }

    private fun <T : Any> visibleNodes(clazz: KClass<T>): List<JsonNode> {
        val deleteFilters = pendingDeletes[clazz].orEmpty()
        val persistedNodes = store.readAll(clazz)
        val pendingNodes = pendingInserts[clazz].orEmpty().map { it.node }

        return (persistedNodes + pendingNodes)
            .filterNot { node -> deleteFilters.any { it.matches(node) } }
    }
}
