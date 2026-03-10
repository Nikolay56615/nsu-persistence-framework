package ru.nsu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.nsu.core.Codec
import ru.nsu.core.JsonSerialStream
import ru.nsu.core.JsonSerializer
import ru.nsu.models.Address
import ru.nsu.models.User
import java.nio.file.Path

class JsonSerialStreamTest {

    private val codec = Codec()
    private val serializer = JsonSerializer(codec)

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `should load objects from strings and files and filter them`() {
        val alice = user(
            id = "u-1",
            name = "Alice",
            active = true
        )
        val bob = user(
            id = "u-2",
            name = "Bob",
            active = false
        )
        val bobFile = tempDir.resolve("bob.json")
        serializer.serialize(bob, bobFile)

        val activeFilter = Filters.eq("is_active", true)
        val bobFilter = PersistFilter { node ->
            node.get("name").asText() == "Bob"
        }

        val stream = JsonSerialStream(User::class, codec)
            .add(serializer.serialize(alice))
            .add(bobFile)

        assertThat(stream.toList()).containsExactly(alice, bob)
        assertThat(stream.toList(activeFilter)).containsExactly(alice)
        assertThat(stream.toListExclude(bobFilter)).containsExactly(alice)
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
