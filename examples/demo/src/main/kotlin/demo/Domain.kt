package demo

import ru.nsu.annotation.PersistField
import ru.nsu.annotation.Persistable

@Persistable(version = 2)
data class User(
    @field:PersistField val id: String,
    @field:PersistField val name: String,
    @field:PersistField(until = 1) val nickname: String? = null,
    @field:PersistField(since = 2) val email: String? = null,
    @field:PersistField val score: Double
)

@Persistable
class Node {
    @PersistField var id: String = ""
    @PersistField var next: Node? = null
}
