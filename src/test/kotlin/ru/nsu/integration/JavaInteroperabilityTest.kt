package ru.nsu.integration

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.nsu.codec.JsonCodec
import ru.nsu.codec.JsonDeserializer
import ru.nsu.codec.JsonSerializer
import ru.nsu.query.Filters
import ru.nsu.storage.JsonSession
import java.nio.file.Path

class JavaInteroperabilityTest {

    private val codec = JsonCodec()
    private val serializer = JsonSerializer(codec)

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `should support immutable java pojos with constructor based deserialization and versioning`() {
        val profile = JavaVersionedProfile(
            "java-1",
            "legacy-java",
            "java@example.com",
            JavaVersionedAddress("Novosibirsk", "630000")
        )

        val jsonV2 = serializer.serialize(profile, 2)
        val nodeV2 = codec.parse(jsonV2)

        assertThat(nodeV2.get("\$version").asInt()).isEqualTo(2)
        assertThat(nodeV2.get("email").asText()).isEqualTo("java@example.com")
        assertThat(nodeV2.get("address").get("\$version").asInt()).isEqualTo(2)
        assertThat(nodeV2.get("address").get("zip_code").asText()).isEqualTo("630000")

        val restoredV2 = JsonDeserializer(JavaVersionedProfile::class, jsonV2, codec, expectedVersion = 2).instance()
        assertThat(restoredV2.id).isEqualTo("java-1")
        assertThat(restoredV2.alias).isNull()
        assertThat(restoredV2.email).isEqualTo("java@example.com")
        assertThat(restoredV2.address.city).isEqualTo("Novosibirsk")
        assertThat(restoredV2.address.zipCode).isEqualTo("630000")

        val jsonV1 = serializer.serialize(profile, 1)
        val nodeV1 = codec.parse(jsonV1)

        assertThat(nodeV1.get("\$version").asInt()).isEqualTo(1)
        assertThat(nodeV1.get("alias").asText()).isEqualTo("legacy-java")
        assertThat(nodeV1.has("email")).isFalse()
        assertThat(nodeV1.get("address").has("zip_code")).isFalse()

        val restoredV1 = JsonDeserializer(JavaVersionedProfile::class, jsonV1, codec, expectedVersion = 1).instance()
        assertThat(restoredV1.id).isEqualTo("java-1")
        assertThat(restoredV1.alias).isEqualTo("legacy-java")
        assertThat(restoredV1.email).isNull()
        assertThat(restoredV1.address.city).isEqualTo("Novosibirsk")
        assertThat(restoredV1.address.zipCode).isNull()

        val session = JsonSession(codec = codec)
            .setDirectory(tempDir)
            .insert(profile, 2)

        val found = session.find(JavaVersionedProfile::class, Filters.eq("address.city", "Novosibirsk"), 2)

        assertThat(found).hasSize(1)
        assertThat(found.single().email).isEqualTo("java@example.com")
    }

    @Test
    fun `should preserve versioned cyclic java graphs`() {
        val root = JavaVersionedNode().apply {
            id = "root"
            alias = "legacy-root"
            note = "root-v2"
        }
        val child = JavaVersionedNode().apply {
            id = "child"
            alias = "legacy-child"
            note = "child-v2"
        }

        root.next = child
        child.next = root

        val jsonV2 = serializer.serialize(root, 2)
        val nodeV2 = codec.parse(jsonV2)

        assertThat(nodeV2.get("\$version").asInt()).isEqualTo(2)
        assertThat(nodeV2.get("note").asText()).isEqualTo("root-v2")
        assertThat(nodeV2.get("next").get("\$version").asInt()).isEqualTo(2)
        assertThat(nodeV2.get("next").get("next").get("\$ref").asText()).isEqualTo("1")

        val restoredV2 = JsonDeserializer(JavaVersionedNode::class, jsonV2, codec, expectedVersion = 2).instance()
        assertThat(restoredV2.id).isEqualTo("root")
        assertThat(restoredV2.alias).isNull()
        assertThat(restoredV2.note).isEqualTo("root-v2")
        assertThat(restoredV2.next).isNotNull
        assertThat(restoredV2.next!!.id).isEqualTo("child")
        assertThat(restoredV2.next!!.note).isEqualTo("child-v2")
        assertThat(restoredV2.next!!.next).isSameAs(restoredV2)

        val jsonV1 = serializer.serialize(root, 1)
        val nodeV1 = codec.parse(jsonV1)

        assertThat(nodeV1.get("\$version").asInt()).isEqualTo(1)
        assertThat(nodeV1.get("alias").asText()).isEqualTo("legacy-root")
        assertThat(nodeV1.has("note")).isFalse()
        assertThat(nodeV1.get("next").get("alias").asText()).isEqualTo("legacy-child")
        assertThat(nodeV1.get("next").get("next").get("\$ref").asText()).isEqualTo("1")

        val restoredV1 = JsonDeserializer(JavaVersionedNode::class, jsonV1, codec, expectedVersion = 1).instance()
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

        val found = session.find(JavaVersionedNode::class, Filters.eq("note", "root-v2"), 2)

        assertThat(found).hasSize(1)
        assertThat(found.single().next!!.next).isSameAs(found.single())
    }

    @Test
    fun `should reject java constructor state that is not fully represented by persisted fields`() {
        val invalid = JavaInvalidConstructorState("java-1", "hidden-state")
        val json = serializer.serialize(invalid)

        assertThat(codec.parse(json).has("internalCode")).isFalse()

        assertThatThrownBy {
            JsonDeserializer(JavaInvalidConstructorState::class, json, codec).instance()
        }.hasMessageContaining("no supported Java constructor found")
            .hasMessageContaining("@PersistField")
    }
}
