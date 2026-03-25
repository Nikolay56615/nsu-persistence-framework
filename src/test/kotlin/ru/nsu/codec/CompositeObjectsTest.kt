package ru.nsu.codec

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.nsu.shared.Address
import ru.nsu.shared.OrderItem
import ru.nsu.shared.OrderStatus
import ru.nsu.shared.User

class CompositeObjectsTest {

    @Test
    fun `should serialize and deserialize nested persistable objects`() {
        val codec = JsonCodec()
        val serializer = JsonSerializer(codec = codec)

        val user = User(
            id = "u-100",
            name = "Alice",
            age = 30,
            address = Address(city = "Novosibirsk", street = "Lenina", zipCode = "630000"),
            active = true,
            tags = setOf("premium", "beta")
        )

        val order = Order(
            id = "ord-1",
            userId = user.id,
            status = OrderStatus.PAID,
            items = listOf(
                OrderItem(sku = "sku-1", quantity = 2, price = 10.5),
                OrderItem(sku = "sku-2", quantity = 1, price = 99.9)
            ),
            metadata = mapOf("channel" to "web", "currency" to "RUB")
        )

        val json = serializer.serialize(order)
        val restored = JsonDeserializer(Order::class, json, codec).instance()

        assertThat(restored).isEqualTo(order)
        assertThat(restored.items).hasSize(2)
        assertThat(restored.status).isEqualTo(OrderStatus.PAID)
        assertThat(restored.metadata["currency"]).isEqualTo("RUB")
    }
}
