package ru.nsu

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.nsu.core.Codec
import ru.nsu.core.JsonSession
import ru.nsu.models.Address
import ru.nsu.models.PlainRecord
import ru.nsu.models.User
import java.nio.file.Files
import java.nio.file.Path

class JsonSessionTest {

    private val codec = Codec()

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `should expose pending inserts persist them and reload from disk`() {
        val alice = user("u-1", "Alice", true)
        val bob = user("u-2", "Bob", false)

        val session = JsonSession(codec = codec)
            .setDirectory(tempDir)
            .insert(alice)
            .insert(bob)

        val activeUsers = session.find(User::class, Filters.eq("is_active", true))

        assertThat(session.find(User::class)).containsExactlyInAnyOrder(alice, bob)
        assertThat(activeUsers).containsExactly(alice)

        session.persist()

        val persistedDirectory = tempDir.resolve("kpersist").resolve(User::class.java.name)
        Files.list(persistedDirectory).use { stream ->
            assertThat(stream.count()).isEqualTo(2)
        }

        val reloaded = JsonSession(tempDir, codec).find(User::class)
        assertThat(reloaded).containsExactlyInAnyOrder(alice, bob)
    }

    @Test
    fun `should apply delete filters to persisted and pending objects`() {
        val alice = user("u-1", "Alice", true)
        val bob = user("u-2", "Bob", false)
        val charlie = user("u-3", "Charlie", true)

        JsonSession(tempDir, codec)
            .insert(alice)
            .insert(bob)
            .persist()

        val deleteFilter = Filters.or(
            Filters.eq("id", "u-2"),
            Filters.eq("id", "u-3")
        )

        val session = JsonSession(tempDir, codec)
            .insert(charlie)
            .delete(User::class, deleteFilter)

        assertThat(session.find(User::class)).containsExactly(alice)

        session.persist()

        val reloaded = JsonSession(tempDir, codec).find(User::class)
        assertThat(reloaded).containsExactly(alice)
    }

    @Test
    fun `should reject non persistable classes in session operations`() {
        val session = JsonSession(codec = codec).setDirectory(tempDir)

        assertThatThrownBy {
            session.insert(PlainRecord("plain-1"))
        }.hasMessageContaining(PlainRecord::class.java.name)

        assertThatThrownBy {
            session.find(PlainRecord::class)
        }.hasMessageContaining(PlainRecord::class.java.name)

        assertThatThrownBy {
            session.delete(PlainRecord::class, Filters.eq("id", "plain-1"))
        }.hasMessageContaining(PlainRecord::class.java.name)
    }

    private fun user(id: String, name: String, active: Boolean): User {
        return User(
            id = id,
            name = name,
            age = 30,
            address = Address(
                city = "Novosibirsk",
                street = "Lenina",
                zipCode = "630000"
            ),
            active = active,
            tags = setOf("test")
        )
    }
}
