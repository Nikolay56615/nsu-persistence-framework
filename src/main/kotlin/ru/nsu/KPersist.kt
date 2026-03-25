package ru.nsu

import ru.nsu.api.Deserializer
import ru.nsu.api.SerialStream
import ru.nsu.api.Serializer
import ru.nsu.api.Session
import ru.nsu.codec.JsonDeserializer
import ru.nsu.codec.JsonSerialStream
import ru.nsu.codec.JsonSerializer
import ru.nsu.storage.JsonSession
import java.nio.file.Path
import kotlin.reflect.KClass

object KPersist {
    fun serializer(): Serializer = JsonSerializer()

    fun session(baseDirectory: Path? = null): Session {
        val session = JsonSession()
        if (baseDirectory != null) {
            session.setDirectory(baseDirectory)
        }
        return session
    }

    fun <T : Any> deserializer(clazz: KClass<T>, json: String): Deserializer<T> = JsonDeserializer(clazz, json)

    fun <T : Any> deserializer(clazz: KClass<T>, json: String, expectedVersion: Int): Deserializer<T> =
        JsonDeserializer(clazz, json, expectedVersion = expectedVersion)

    fun <T : Any> stream(clazz: KClass<T>): SerialStream<T> = JsonSerialStream(clazz)
}

inline fun <reified T : Any> deserializer(json: String): Deserializer<T> = KPersist.deserializer(T::class, json)

inline fun <reified T : Any> deserializer(json: String, expectedVersion: Int): Deserializer<T> =
    KPersist.deserializer(T::class, json, expectedVersion)

inline fun <reified T : Any> serialStream(): SerialStream<T> = KPersist.stream(T::class)
