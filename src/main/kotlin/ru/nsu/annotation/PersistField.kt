package ru.nsu.annotation

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class PersistField(
    val name: String = "",
    val since: Int = 1,
    val until: Int = Int.MAX_VALUE
)
