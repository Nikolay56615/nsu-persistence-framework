package ru.nsu.models

import ru.nsu.annotation.PersistField
import ru.nsu.annotation.Persistable

@Persistable
class MutableRecord() {
    @PersistField
    var id: String = ""

    @PersistField
    var age: Int = 0

    var nickname: String? = "n/a"
}
