package ru.nsu.query

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.nsu.codec.JsonCodec
import ru.nsu.exception.InvalidFilterException
import ru.nsu.fixture.OrderStatus

class FiltersTest {

    private val codec = JsonCodec()

    @Test
    fun `should compare scalar values and nested paths`() {
        val node = json(
            """
            {
              "id": "u-1",
              "age": 30,
              "is_active": true,
              "status": "PAID",
              "address": {
                "city": "Novosibirsk"
              }
            }
            """
        )

        assertThat(Filters.eq("id", "u-1").matches(node)).isTrue()
        assertThat(Filters.ne("id", "u-2").matches(node)).isTrue()
        assertThat(Filters.eq("age", 30).matches(node)).isTrue()
        assertThat(Filters.gt("age", 20).matches(node)).isTrue()
        assertThat(Filters.gte("age", 30).matches(node)).isTrue()
        assertThat(Filters.lt("age", 31).matches(node)).isTrue()
        assertThat(Filters.lte("age", 30).matches(node)).isTrue()
        assertThat(Filters.eq("is_active", true).matches(node)).isTrue()
        assertThat(Filters.eq("status", OrderStatus.PAID).matches(node)).isTrue()
        assertThat(Filters.eq("address.city", "Novosibirsk").matches(node)).isTrue()
        assertThat(Filters.eq("status", OrderStatus.NEW).matches(json("""{"status":"NEW"}"""))).isTrue()
        assertThat(Filters.eq("status", OrderStatus.CANCELLED).matches(json("""{"status":"CANCELLED"}"""))).isTrue()
    }

    @Test
    fun `should compare strings lexicographically and combine filters`() {
        val node = json(
            """
            {
              "name": "Bob",
              "address": {
                "city": "Novosibirsk"
              },
              "is_active": false
            }
            """
        )

        val composed = Filters.and(
            Filters.gt("name", "Alice"),
            Filters.or(
                Filters.eq("address.city", "Novosibirsk"),
                Filters.eq("address.city", "Omsk")
            ),
            Filters.not(Filters.eq("is_active", true))
        )

        assertThat(composed.matches(node)).isTrue()
        assertThat(Filters.lte("name", "Bob").matches(node)).isTrue()
    }

    @Test
    fun `should treat missing fields as null for equality`() {
        val node = json("""{"id":"u-1"}""")

        assertThat(Filters.eq("nickname", null).matches(node)).isTrue()
        assertThat(Filters.eq("nickname", "Alice").matches(node)).isFalse()
        assertThat(Filters.ne("nickname", null).matches(node)).isFalse()
    }

    @Test
    fun `should reject invalid path definitions`() {
        assertThatThrownBy { Filters.eq("", "value") }
            .isInstanceOf(InvalidFilterException::class.java)
            .hasMessageContaining("cannot be blank")

        assertThatThrownBy { Filters.eq("address..city", "value") }
            .isInstanceOf(InvalidFilterException::class.java)
            .hasMessageContaining("empty path segment")

        assertThatThrownBy { Filters.eq("items[0].sku", "sku-1") }
            .isInstanceOf(InvalidFilterException::class.java)
            .hasMessageContaining("array indexing is not supported")
    }

    @Test
    fun `should reject invalid logical combinations and expected values`() {
        assertThatThrownBy { Filters.and() }
            .isInstanceOf(InvalidFilterException::class.java)
            .hasMessageContaining("AND filter requires")

        assertThatThrownBy { Filters.or() }
            .isInstanceOf(InvalidFilterException::class.java)
            .hasMessageContaining("OR filter requires")

        assertThatThrownBy { Filters.eq("tags", listOf("test")) }
            .isInstanceOf(InvalidFilterException::class.java)
            .hasMessageContaining("Unsupported expected value type")
    }

    @Test
    fun `should reject incompatible or unsupported comparisons`() {
        val node = json(
            """
            {
              "age": 30,
              "name": "Alice",
              "address": {
                "city": "Novosibirsk"
              },
              "items": [
                {"sku":"sku-1"}
              ]
            }
            """
        )

        assertThatThrownBy { Filters.eq("age", "30").matches(node) }
            .isInstanceOf(InvalidFilterException::class.java)
            .hasMessageContaining("Cannot compare values of types")

        assertThatThrownBy { Filters.gt("missing_age", 18).matches(node) }
            .isInstanceOf(InvalidFilterException::class.java)
            .hasMessageContaining("comparison to null")

        assertThatThrownBy { Filters.eq("address", "value").matches(node) }
            .isInstanceOf(InvalidFilterException::class.java)
            .hasMessageContaining("object value")

        assertThatThrownBy { Filters.eq("items", "value").matches(node) }
            .isInstanceOf(InvalidFilterException::class.java)
            .hasMessageContaining("array value")

        assertThatThrownBy { Filters.eq("items.sku", "sku-1").matches(node) }
            .isInstanceOf(InvalidFilterException::class.java)
            .hasMessageContaining("array traversal is not supported")

        assertThatThrownBy { Filters.eq("age.value", 1).matches(node) }
            .isInstanceOf(InvalidFilterException::class.java)
            .hasMessageContaining("does not reference an object value")
    }

    private fun json(source: String) = codec.parse(source.trimIndent())
}
