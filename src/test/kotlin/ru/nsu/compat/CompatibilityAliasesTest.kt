package ru.nsu.compat

import com.fasterxml.jackson.databind.JsonNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.nsu.core.Codec
import ru.nsu.core.JsonDeserializer
import ru.nsu.core.JsonSerialStream
import ru.nsu.core.JsonSerializer
import ru.nsu.core.JsonSession
import ru.nsu.fixture.OrderItem
import ru.nsu.query.Filters
import ru.nsu.query.PersistFilter
import java.nio.file.Path

class CompatibilityAliasesTest {

    private val codec = Codec()
    private val serializer = JsonSerializer(codec)

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `legacy core aliases remain usable with canonical query api`() {
        val item = OrderItem(sku = "sku-1", quantity = 2, price = 10.5)
        val json = serializer.serialize(item)
        val node = codec.parse(json)

        val restored = JsonDeserializer(OrderItem::class, json, codec).instance()
        val session = JsonSession(codec = codec)
            .setDirectory(tempDir)
            .insert(item)
        val stream = JsonSerialStream(OrderItem::class, codec).add(json)
        val quantityFilter = PersistFilter { candidate: JsonNode -> candidate.get("quantity").asInt() == 2 }

        assertThat(restored).isEqualTo(item)
        assertThat(Filters.eq("sku", "sku-1").matches(node)).isTrue()
        assertThat(quantityFilter.matches(node)).isTrue()
        assertThat(stream.toListExclude { false }).containsExactly(item)
        assertThat(session.find(OrderItem::class, Filters.eq("sku", "sku-1"))).containsExactly(item)
    }
}
