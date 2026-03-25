package ru.nsu.codec

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.nsu.shared.OrderItem
import ru.nsu.shared.PlainRecord
import java.nio.file.Files
import java.nio.file.Path

class JsonSerializationCoverageTest {

    private val codec = JsonCodec()
    private val serializer = JsonSerializer(codec)

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `should serialize aliased fields and skip ignored ones`() {
        val file = tempDir.resolve("annotated-record.json")
        val record = AnnotatedRecord(
            id = "rec-1",
            name = "Alice",
            secret = "top-secret"
        )

        serializer.serialize(record, file)

        val node = codec.parse(file)
        val restored = JsonDeserializer(AnnotatedRecord::class, Files.readString(file), codec).instance()

        assertThat(node.get("id").asText()).isEqualTo("rec-1")
        assertThat(node.get("display_name").asText()).isEqualTo("Alice")
        assertThat(node.has("secret")).isFalse()
        assertThat(restored).isEqualTo(AnnotatedRecord(id = "rec-1", name = "Alice"))
    }

    @Test
    fun `should deserialize collections and typed maps`() {
        val collectionJson = """
            [
              {"sku":"sku-1","quantity":2,"price":10.5},
              {"sku":"sku-2","quantity":1,"price":99.9}
            ]
        """.trimIndent()
        val mapJson = """
            {
              "1":{"sku":"sku-1","quantity":2,"price":10.5},
              "2":{"sku":"sku-2","quantity":1,"price":99.9}
            }
        """.trimIndent()

        val items = JsonDeserializer(OrderItem::class, collectionJson, codec).collection()
        val itemMap = JsonDeserializer(OrderItem::class, mapJson, codec).map(Int::class)

        assertThat(items).containsExactly(
            OrderItem("sku-1", 2, 10.5),
            OrderItem("sku-2", 1, 99.9)
        )
        assertThat(itemMap).containsExactlyEntriesOf(
            linkedMapOf(
                1 to OrderItem("sku-1", 2, 10.5),
                2 to OrderItem("sku-2", 1, 99.9)
            )
        )
    }

    @Test
    fun `should support constructor defaults and no-arg object instantiation`() {
        val defaulted = JsonDeserializer(
            DefaultedRecord::class,
            """{"required":"value"}""",
            codec
        ).instance()
        val mutable = JsonDeserializer(
            MutableRecord::class,
            """{"id":"mutable-1","age":42}""",
            codec
        ).instance()

        assertThat(defaulted).isEqualTo(DefaultedRecord(required = "value", count = 7))
        assertThat(mutable.id).isEqualTo("mutable-1")
        assertThat(mutable.age).isEqualTo(42)
        assertThat(mutable.nickname).isEqualTo("n/a")
    }

    @Test
    fun `should reject invalid roots for deserializer entry points`() {
        assertThatThrownBy {
            JsonDeserializer(OrderItem::class, "[]", codec).instance()
        }.hasMessageContaining("Expected JSON object")

        assertThatThrownBy {
            JsonDeserializer(OrderItem::class, """{"sku":"sku-1"}""", codec).collection()
        }.hasMessageContaining("Expected JSON array")

        assertThatThrownBy {
            JsonDeserializer(OrderItem::class, """{"1":"bad"}""", codec).map(Int::class)
        }.hasMessageContaining("Map values must be JSON objects")
    }

    @Test
    fun `should reject unsupported or misconfigured classes`() {
        assertThatThrownBy {
            serializer.serialize(PlainRecord("plain-1"))
        }.hasMessageContaining("Unsupported type")

        assertThatThrownBy {
            serializer.serialize(DuplicateJsonNames("one", "two"))
        }.hasMessageContaining("duplicate JSON field names")

        assertThatThrownBy {
            serializer.serialize(NoPersistFieldsRecord("missing"))
        }.hasMessageContaining("@PersistField")

        assertThatThrownBy {
            serializer.serialize(LegacyAnnotatedRecord(name = "legacy"))
        }.hasMessageContaining("@PersistField")
    }

    @Test
    fun `should reject required constructor parameters that are not persisted`() {
        assertThatThrownBy {
            serializer.serialize(InvalidConstructorRecord(id = "rec-1", version = 2))
        }.hasMessageContaining("Required constructor parameter")
    }
}

@ru.nsu.annotation.Persistable
data class InvalidConstructorRecord(
    @field:ru.nsu.annotation.PersistField
    val id: String,
    val version: Int
)
