package ru.nsu.demo

import ru.nsu.KPersist
import ru.nsu.query.Filters
import java.nio.file.Path

fun main() {
    val baseDirectory = Path.of("build", "demo-session")
    val serializer = KPersist.serializer()
    val session = KPersist.session(baseDirectory)

    val alice = DemoUser(
        id = "user-1",
        name = "Alice",
        active = true,
        address = DemoAddress(
            city = "Novosibirsk",
            street = "Lenina",
            zipCode = "630000"
        ),
        tags = linkedSetOf("premium", "beta"),
        email = "alice@example.com"
    )
    val bob = DemoUser(
        id = "user-2",
        name = "Bob",
        active = false,
        address = DemoAddress(
            city = "Tomsk",
            street = "Prospekt Mira",
            zipCode = "634000"
        ),
        tags = linkedSetOf("trial"),
        email = "bob@example.com"
    )
    val order = DemoOrder(
        id = "order-1",
        userId = alice.id,
        status = DemoOrderStatus.PAID,
        items = listOf(
            DemoOrderItem("sku-1", 2, 10.5),
            DemoOrderItem("sku-2", 1, 99.9)
        ),
        metadata = linkedMapOf("channel" to "web", "currency" to "RUB")
    )

    println("Current DemoUser JSON (v2):")
    println(serializer.serialize(alice))
    println()
    println("Backward-compatible DemoUser JSON (v1):")
    println(serializer.serialize(alice, 1))
    println()

    session
        .insert(alice)
        .insert(bob)
        .insert(order)
        .persist()

    val activeUsers = session.find(DemoUser::class, Filters.eq("is_active", true))
    println("Active users:")
    activeUsers.forEach(::println)
    println()

    session.delete(DemoUser::class, Filters.eq("id", "user-2")).persist()

    val remainingUsers = session.find(DemoUser::class)
    println("Remaining users after delete:")
    remainingUsers.forEach(::println)
    println()

    println("Persisted files are stored under: $baseDirectory/kpersist")
}
