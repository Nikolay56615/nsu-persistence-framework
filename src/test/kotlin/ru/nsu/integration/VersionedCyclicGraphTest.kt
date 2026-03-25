package ru.nsu.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.nsu.annotation.PersistField
import ru.nsu.annotation.Persistable
import ru.nsu.codec.JsonCodec
import ru.nsu.codec.JsonDeserializer
import ru.nsu.codec.JsonSerializer
import ru.nsu.query.Filters
import ru.nsu.storage.JsonSession
import java.nio.file.Path

class VersionedCyclicGraphTest {

    private val codec = JsonCodec()
    private val serializer = JsonSerializer(codec)

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `should preserve cycles and versioned fields in serializer deserializer and session`() {
        val root = VersionedNode().apply {
            id = "root"
            alias = "legacy-root"
            note = "v2-root"
        }
        val child = VersionedNode().apply {
            id = "child"
            alias = "legacy-child"
            note = "v2-child"
        }

        root.next = child
        child.next = root

        val jsonV2 = serializer.serialize(root, 2)
        val nodeV2 = codec.parse(jsonV2)

        assertThat(nodeV2.get("\$version").asInt()).isEqualTo(2)
        assertThat(nodeV2.get("note").asText()).isEqualTo("v2-root")
        assertThat(nodeV2.get("next").get("\$version").asInt()).isEqualTo(2)
        assertThat(nodeV2.get("next").get("note").asText()).isEqualTo("v2-child")
        assertThat(nodeV2.get("next").get("next").get("\$ref").asText()).isEqualTo("1")

        val restoredV2 = JsonDeserializer(VersionedNode::class, jsonV2, codec, expectedVersion = 2).instance()
        assertThat(restoredV2.id).isEqualTo("root")
        assertThat(restoredV2.note).isEqualTo("v2-root")
        assertThat(restoredV2.next).isNotNull
        assertThat(restoredV2.next!!.id).isEqualTo("child")
        assertThat(restoredV2.next!!.note).isEqualTo("v2-child")
        assertThat(restoredV2.next!!.next).isSameAs(restoredV2)

        val jsonV1 = serializer.serialize(root, 1)
        val nodeV1 = codec.parse(jsonV1)

        assertThat(nodeV1.get("\$version").asInt()).isEqualTo(1)
        assertThat(nodeV1.get("alias").asText()).isEqualTo("legacy-root")
        assertThat(nodeV1.has("note")).isFalse()
        assertThat(nodeV1.get("next").get("alias").asText()).isEqualTo("legacy-child")
        assertThat(nodeV1.get("next").has("note")).isFalse()
        assertThat(nodeV1.get("next").get("next").get("\$ref").asText()).isEqualTo("1")

        val restoredV1 = JsonDeserializer(VersionedNode::class, jsonV1, codec, expectedVersion = 1).instance()
        assertThat(restoredV1.id).isEqualTo("root")
        assertThat(restoredV1.alias).isEqualTo("legacy-root")
        assertThat(restoredV1.note).isNull()
        assertThat(restoredV1.next).isNotNull
        assertThat(restoredV1.next!!.alias).isEqualTo("legacy-child")
        assertThat(restoredV1.next!!.note).isNull()
        assertThat(restoredV1.next!!.next).isSameAs(restoredV1)

        val session = JsonSession(codec = codec)
            .setDirectory(tempDir)
            .insert(root, 2)

        val found = session.find(VersionedNode::class, Filters.eq("note", "v2-root"), 2)

        assertThat(found).hasSize(1)
        val fromSession = found.single()
        assertThat(fromSession.id).isEqualTo("root")
        assertThat(fromSession.note).isEqualTo("v2-root")
        assertThat(fromSession.next).isNotNull
        assertThat(fromSession.next!!.id).isEqualTo("child")
        assertThat(fromSession.next!!.next).isSameAs(fromSession)
    }
}

@Persistable(version = 2)
class VersionedNode {
    @PersistField
    var id: String = ""

    @PersistField(until = 1)
    var alias: String? = null

    @PersistField(since = 2)
    var note: String? = null

    @PersistField
    var next: VersionedNode? = null
}
