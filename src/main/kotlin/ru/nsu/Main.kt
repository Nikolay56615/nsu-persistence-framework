package ru.nsu

import ru.nsu.annotation.PersistField
import ru.nsu.annotation.Persistable
import ru.nsu.core.Codec
import ru.nsu.core.JsonDeserializer
import ru.nsu.core.JsonSerializer
import ru.nsu.core.JsonSession
import java.nio.file.Files

fun main() {
    val codec = Codec()
    val serializer = JsonSerializer(codec)
    val storageDir = Files.createTempDirectory("kpersist-demo")

    val alice = DemoUser(
        id = "u-1",
        name = "Alice",
        age = 30,
        address = DemoAddress(
            city = "Novosibirsk",
            street = "Lenina",
            zipCode = "630000"
        ),
        active = true
    )
    val bob = DemoUser(
        id = "u-2",
        name = "Bob",
        age = 17,
        address = DemoAddress(
            city = "Omsk",
            street = "Mira",
            zipCode = "644000"
        ),
        active = false
    )

    println("=== Serialize object to JSON ===")
    val json = serializer.serialize(alice)
    println(json)

    println()
    println("=== Deserialize JSON back to object ===")
    val restored = JsonDeserializer(DemoUser::class, json, codec).instance()
    println(restored)

    println()
    println("=== Persist objects and query them with filters ===")
    val session = JsonSession(storageDir, codec)
        .insert(alice)
        .insert(bob)

    val activeAdultsFromPending = session.find(
        DemoUser::class,
        Filters.and(
            Filters.eq("is_active", true),
            Filters.gte("age", 18),
            Filters.eq("address.city", "Novosibirsk")
        )
    )
    println("Active adults before persist(): $activeAdultsFromPending")

    session.persist()
    println("Saved JSON files to: ${storageDir.toAbsolutePath()}")

    val reloadedSession = JsonSession(storageDir, codec)
    val activeUsersFromDisk = reloadedSession.find(DemoUser::class, Filters.eq("is_active", true))
    println("Active users loaded from disk: $activeUsersFromDisk")
}

@Persistable
data class DemoUser(
    @field:PersistField
    val id: String,
    @field:PersistField
    val name: String,
    @field:PersistField
    val age: Int,
    @field:PersistField
    val address: DemoAddress,
    @field:PersistField(name = "is_active")
    val active: Boolean
)

@Persistable
data class DemoAddress(
    @field:PersistField
    val city: String,
    @field:PersistField
    val street: String,
    @field:PersistField
    val zipCode: String
)
