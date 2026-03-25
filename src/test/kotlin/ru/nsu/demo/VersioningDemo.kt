package ru.nsu.demo

import ru.nsu.annotation.PersistField
import ru.nsu.annotation.Persistable
import ru.nsu.codec.JsonDeserializer
import ru.nsu.codec.JsonSerializer

@Persistable(version = 3)
data class UserProfile(
    @field:PersistField
    val id: String,

    @field:PersistField(until = 2)
    val nickname: String? = null,

    @field:PersistField(since = 2)
    val email: String? = null
)

fun main() {
    val serializer = JsonSerializer()

    val user = UserProfile(
        id = "u-1",
        nickname = "ally",
        email = "alice@example.com"
    )

    val jsonV1 = serializer.serialize(user, 1)
    println(jsonV1)

    val jsonV3 = serializer.serialize(user)
    println(jsonV3)

    val restoredV1 = JsonDeserializer(UserProfile::class, jsonV1, expectedVersion = 1).instance()
    println(restoredV1)

    val restoredV3 = JsonDeserializer(UserProfile::class, jsonV3).instance()
    println(restoredV3)
}
