package ru.nsu

import com.fasterxml.jackson.databind.ObjectMapper
import ru.nsu.annotation.PersistIgnore
import ru.nsu.annotation.PersistName
import ru.nsu.annotation.Persistable
import ru.nsu.core.JsonDeserializer
import ru.nsu.core.JsonSerializer
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistenceAuditTest {
    private val serializer = JsonSerializer()
    private val mapper = ObjectMapper()

    @Test
    fun `opt-out serializes backing fields and skips computed properties and ignored state`() {
        val rectangle = Rectangle(width = 3, height = 4, label = "demo", cache = "skip-me")

        val json = serializer.serialize(rectangle)
        val node = mapper.readTree(json)

        assertEquals(3, node.get("width").asInt())
        assertEquals(4, node.get("height").asInt())
        assertEquals("demo", node.get("title").asText())
        assertFalse(node.has("cache"))
        assertFalse(node.has("area"))
        assertFalse(node.has("describe"))

        val restored = JsonDeserializer(Rectangle::class, json).instance()
        assertEquals(12, restored.area)
        assertEquals("skip-default", restored.cache)
    }

    @Test
    fun `serializer handles nested objects null collections enums and java classes`() {
        val owner = Person(
            name = "Vladimir",
            age = 21,
            role = Role.ADMIN,
            address = Address("Novosibirsk", "Lenina"),
            tags = listOf("student", "backend"),
            nickname = null
        )

        val json = serializer.serialize(owner)
        val restored = JsonDeserializer(Person::class, json).instance()

        assertEquals(owner, restored)
        assertNull(restored.nickname)

        val javaProfile = JavaProfile()
        javaProfile.id = "java-1"
        javaProfile.level = 7
        javaProfile.secret = "hidden"

        val javaJson = serializer.serialize(javaProfile)
        val javaNode = mapper.readTree(javaJson)
        assertEquals("java-1", javaNode.get("id").asText())
        assertEquals(7, javaNode.get("lvl").asInt())
        assertFalse(javaNode.has("secret"))
        assertFalse(javaNode.has("label"))

        val restoredJava = JsonDeserializer(JavaProfile::class, javaJson).instance()
        assertEquals("java-1", restoredJava.id)
        assertEquals(7, restoredJava.level)
        assertNull(restoredJava.secret)
    }

    @Test
    fun `stream and session can load and filter persisted json`() {
        val first = Rectangle(width = 2, height = 5, label = "small")
        val second = Rectangle(width = 7, height = 3, label = "big")

        val stream = serialStream<Rectangle>()
            .add(serializer.serialize(first))
            .add(serializer.serialize(second))

        val filtered = stream.toList(Filters.gt("width", 3))
        assertEquals(listOf(second), filtered)

        val directory = createTempDirectory("kpersist-audit")
        val session = KPersist.session(directory)
        session.insert(first).insert(second).persist()

        val stored = session.find(Rectangle::class)
        assertEquals(2, stored.size)

        val onlyBig = session.find(Rectangle::class, Filters.eq("title", "big"))
        assertEquals(listOf(second), onlyBig)

        session.delete(Rectangle::class, Filters.lt("width", 3)).persist()
        val afterDelete = session.find(Rectangle::class)
        assertEquals(listOf(second), afterDelete)
    }

    @Test
    fun `serializer and deserializer preserve cyclic references via id and ref`() {
        val first = MutableNode()
        val second = MutableNode()
        first.name = "first"
        second.name = "second"
        first.next = second
        second.next = first

        val json = serializer.serialize(first)
        val node = mapper.readTree(json)

        assertTrue(node.has("\$id"))
        assertEquals("1", node.get("\$id").asText())
        assertEquals("2", node.get("next").get("\$id").asText())
        assertEquals("1", node.get("next").get("next").get("\$ref").asText())

        val restored = JsonDeserializer(MutableNode::class, json).instance()
        assertEquals("first", restored.name)
        val restoredNext = restored.next
        assertNotNull(restoredNext)
        assertEquals("second", restoredNext.name)
        assertSame(restored, restoredNext.next)
    }

    @Test
    fun `deserializer throws clear error for cyclic constructor-only classes`() {
        val json = $$"""
            {
              "$id": "1",
              "name": "root",
              "next": {
                "$id": "2",
                "name": "child",
                "next": { "$ref": "1" }
              }
            }
        """.trimIndent()

        val exception = assertFailsWith<ru.nsu.exception.DeserializationException> {
            JsonDeserializer(ConstructorNode::class, json).instance()
        }

        assertTrue(exception.message!!.contains("cannot be pre-created"))
    }
}

@Persistable
data class Rectangle(
    val width: Int,
    val height: Int,
    @field:PersistName("title")
    val label: String,
    @field:PersistIgnore
    val cache: String = "skip-default"
) {
    val area: Int
        get() = width * height

    fun describe(): String = "$label:$width x $height"
}

@Persistable
data class Address(
    val city: String,
    val street: String
)

enum class Role {
    USER,
    ADMIN
}

@Persistable
data class Person(
    val name: String,
    val age: Int,
    val role: Role,
    val address: Address,
    val tags: List<String>,
    val nickname: String?
)

@Persistable
class MutableNode {
    var name: String = ""
    var next: MutableNode? = null
}

@Persistable
data class ConstructorNode(
    val name: String,
    val next: ConstructorNode?
)
