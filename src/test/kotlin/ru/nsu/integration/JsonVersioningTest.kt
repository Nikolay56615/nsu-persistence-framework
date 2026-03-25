package ru.nsu.integration

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.nsu.deserializer
import ru.nsu.codec.JsonCodec
import ru.nsu.codec.JsonDeserializer
import ru.nsu.codec.JsonSerialStream
import ru.nsu.codec.JsonSerializer
import ru.nsu.fixture.InvalidPersistableVersionRecord
import ru.nsu.fixture.InvalidRequiredVersionedRecord
import ru.nsu.fixture.InvalidVersionFieldRangeRecord
import ru.nsu.fixture.InvalidVersionNameRecord
import ru.nsu.fixture.InvalidVersionUntilRecord
import ru.nsu.fixture.UnsupportedNestedVersionEnvelope
import ru.nsu.fixture.VersionedAddress
import ru.nsu.fixture.VersionedUser
import ru.nsu.storage.JsonSession
import java.nio.file.Path

class JsonVersioningTest {

    private val codec = JsonCodec()
    private val serializer = JsonSerializer(codec)

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `should write current version by default and include nested version fields`() {
        val user = versionedUser()

        val json = serializer.serialize(user)
        val node = codec.parse(json)

        assertThat(node.get("\$version").asInt()).isEqualTo(3)
        assertThat(node.get("id").asText()).isEqualTo("u-1")
        assertThat(node.get("address").get("\$version").asInt()).isEqualTo(3)
        assertThat(node.has("nickname")).isFalse()
        assertThat(node.get("email").asText()).isEqualTo("alice@example.com")
        assertThat(node.get("address").get("zip_code").asText()).isEqualTo("630000")
    }

    @Test
    fun `should gate fields by explicit write version and read old json without version as v1`() {
        val user = versionedUser()

        val versionOneJson = serializer.serialize(user, 1)
        val versionOneNode = codec.parse(versionOneJson)
        val restoredVersionOne = deserializer<VersionedUser>(versionOneJson, 1).instance()
        val oldJson = """
            {
              "id": "u-legacy",
              "address": {
                "city": "Novosibirsk"
              }
            }
        """.trimIndent()
        val restoredLegacy = deserializer<VersionedUser>(oldJson).instance()

        assertThat(versionOneNode.get("\$version").asInt()).isEqualTo(1)
        assertThat(versionOneNode.has("email")).isFalse()
        assertThat(versionOneNode.get("nickname").asText()).isEqualTo("ally")
        assertThat(versionOneNode.get("address").has("zip_code")).isFalse()

        assertThat(restoredVersionOne).isEqualTo(
            VersionedUser(
                id = "u-1",
                nickname = "ally",
                email = null,
                address = VersionedAddress(city = "Novosibirsk")
            )
        )
        assertThat(restoredLegacy).isEqualTo(
            VersionedUser(
                id = "u-legacy",
                nickname = null,
                email = null,
                address = VersionedAddress(city = "Novosibirsk")
            )
        )
    }

    @Test
    fun `should reject expected version mismatches in deserializer stream and session`() {
        val user = versionedUser()
        val versionOneJson = serializer.serialize(user, 1)
        val versionThreeJson = serializer.serialize(user, 3)
        val session = JsonSession(codec = codec)
            .setDirectory(tempDir)
            .insert(user, 1)
            .insert(user.copy(id = "u-2"), 3)

        assertThatThrownBy {
            JsonDeserializer(VersionedUser::class, versionThreeJson, codec, expectedVersion = 2).instance()
        }.hasMessageContaining("Expected document version 2")

        val stream = JsonSerialStream(VersionedUser::class, codec)
            .add(versionOneJson)
            .add(versionThreeJson)

        assertThatThrownBy {
            stream.toList(3)
        }.hasMessageContaining("Expected document version 3")

        assertThat(session.find(VersionedUser::class)).hasSize(2)
        assertThatThrownBy {
            session.find(VersionedUser::class, 3)
        }.hasMessageContaining("Expected document version 3")
    }

    @Test
    fun `should validate version metadata and nested class compatibility`() {
        assertThatThrownBy {
            serializer.serialize(InvalidPersistableVersionRecord("bad"))
        }.hasMessageContaining("@Persistable")

        assertThatThrownBy {
            serializer.serialize(InvalidVersionFieldRangeRecord("bad"))
        }.hasMessageContaining("since")

        assertThatThrownBy {
            serializer.serialize(InvalidVersionUntilRecord("bad"))
        }.hasMessageContaining("until")

        assertThatThrownBy {
            serializer.serialize(InvalidVersionNameRecord("bad"))
        }.hasMessageContaining("\$version")

        assertThatThrownBy {
            serializer.serialize(InvalidRequiredVersionedRecord("bad"))
        }.hasMessageContaining("active for all supported versions")

        assertThatThrownBy {
            serializer.serialize(
                UnsupportedNestedVersionEnvelope(
                    id = "env-1",
                    legacy = UnsupportedNestedVersionEnvelope.LegacyNested("nested")
                ),
                2
            )
        }.hasMessageContaining("does not support version 2")
    }

    private fun versionedUser(): VersionedUser {
        return VersionedUser(
            id = "u-1",
            nickname = "ally",
            email = "alice@example.com",
            address = VersionedAddress(
                city = "Novosibirsk",
                zipCode = "630000"
            )
        )
    }
}
