package demo

import ru.nsu.KPersist
import ru.nsu.deserializer
import ru.nsu.query.Filters
import ru.nsu.serialStream
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory

fun main() {
    val serializer = KPersist.serializer()
    val user = User(
        id = "u-1",
        name = "Alice",
        nickname = "ally",
        email = "alice@example.com",
        score = 72.0
    )
    val lowUser = User(
        id = "u-2",
        name = "Bob",
        nickname = "bobby",
        email = "bob@example.com",
        score = 15.0
    )
    val strongUser = User(
        id = "u-3",
        name = "Carol",
        nickname = "caz",
        email = "carol@example.com",
        score = 90.0
    )

    println("1. Serialize / deserialize")
    val json = serializer.serialize(user)
    val restored = deserializer<User>(json).instance()
    println(json)
    println("restored = $restored")
    println()

    val outputFile = Path.of("build", "demo-output", "user.json").toAbsolutePath()
    outputFile.parent.createDirectories()
    serializer.serialize(user, outputFile)
    println("Файл: $outputFile")
    println()

    println("2. Версионирование")
    val oldJson = serializer.serialize(user, 1)
    val restoredOld = deserializer<User>(oldJson).instance()
    println(oldJson)
    println("nickname = ${restoredOld.nickname}, email = ${restoredOld.email}")
    println()

    println("3. SerialStream")
    val streamUsers = serialStream<User>()
        .add(json)
        .add(serializer.serialize(lowUser))
        .add(serializer.serialize(strongUser, 1))
        .toList()
        .sortedBy { it.id }
    val strongUsers = serialStream<User>()
        .add(json)
        .add(serializer.serialize(lowUser))
        .add(serializer.serialize(strongUser, 1))
        .toList(Filters.gt("score", 50.0))
        .sortedBy { it.id }
    println("all = ${streamUsers.map { it.id }}")
    println("score > 50 = ${strongUsers.map { it.id }}")
    println()

    println("4. Session + filter + delete")
    val tempDir = createTempDirectory("kpersist-demo-session")
    try {
        val session = KPersist.session(tempDir)
        session
            .insert(user)
            .insert(lowUser)
            .insert(strongUser)
            .persist()

        val found = session.find(User::class, Filters.gt("score", 50.0)).sortedBy { it.id }
        println("score > 50 = ${found.map { it.id }}")

        session
            .delete(User::class, Filters.lt("score", 20.0))
            .persist()

        val remaining = session.find(User::class).sortedBy { it.id }
        println("after delete = ${remaining.map { it.id }}")
    } finally {
        tempDir.toFile().deleteRecursively()
    }
    println()

    println("5. Циклические ссылки")
    val first = Node().apply { id = "n-1" }
    val second = Node().apply { id = "n-2" }
    first.next = second
    second.next = first

    val cycleJson = serializer.serialize(first)
    val restoredNode = deserializer<Node>(cycleJson).instance()
    println(cycleJson)
    println("cycle restored = ${restoredNode.next?.next === restoredNode}")
}
