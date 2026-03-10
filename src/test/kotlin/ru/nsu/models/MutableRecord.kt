package ru.nsu.models

import ru.nsu.annotation.Persistable

@Persistable
class MutableRecord() {
    var id: String = ""
    var age: Int = 0
}
