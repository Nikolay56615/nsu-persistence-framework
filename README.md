# NSU Persistence Framework

Reflection-based object persistence framework for Kotlin/JVM and Java POJOs. It serializes opt-in object graphs to JSON, supports field-level versioning, preserves cyclic object identity with `$id`/`$ref`, and filters raw JSON before instantiating domain objects.

## What The Project Supports

- `@Persistable` marks entity classes that may be serialized as object nodes.
- `@PersistField(name, since, until)` is the only supported persistence-field contract.
- Supported value kinds: primitives, boxed primitives, `String`, `Boolean`, `Char`, enums, arrays, `List`, `Set`, `Map`, and nested `@Persistable` objects.
- Raw JSON filtering with `AND`, `OR`, `NOT`, scalar comparisons, and nested paths like `address.city`.
- Session storage in `<base>/kpersist/<fqcn>/<uuid>.json`.
- Cyclic graph support via identity tracking on write and two-phase reconstruction on read.

## Final Annotation Contract

The framework uses an opt-in data model:

- Only fields annotated with `@PersistField` are serialized and deserialized.
- Computed properties, methods, transient state, and unannotated fields are ignored.

This matches the project clarification that only data should cross the wire, not behavior or derived state.

## JSON Shape

Persistable objects are written as JSON objects with framework metadata:

```json
{
  "$id": "1",
  "$version": 2,
  "id": "user-1",
  "address": {
    "$id": "2",
    "$version": 2,
    "city": "Novosibirsk",
    "street": "Lenina",
    "zipCode": "630000"
  }
}
```

If the same object instance is reached again during the same serialization pass, it is emitted as a reference:

```json
{ "$ref": "1" }
```

## Architecture

- `ru.nsu.codec`
  - `JsonCodec` ties parsing, encoding, and decoding together.
  - `JsonValueEncoder` walks supported values and emits Jackson tree nodes without delegating whole-object serialization to Jackson.
  - `JsonValueDecoder` reconstructs values from JSON trees, manages `$id`/`$ref`, and enforces version compatibility.
  - `ObjectInstantiator` restores classes through Kotlin primary constructors or accessible no-arg constructors.
- `ru.nsu.metadata`
  - `ReflectionPersistMetadataResolver` inspects fields, validates annotations, and prepares version-aware field metadata.
- `ru.nsu.query`
  - `Filters` evaluates JSON trees before object creation, which enables partial loading.
- `ru.nsu.storage`
  - `JsonSession` tracks pending inserts/deletes and persists visible JSON documents through `FileSystemJsonDocumentStore`.

## Versioning Rules

- `@Persistable(version = N)` declares the maximum document version supported by a class.
- `@PersistField(since, until)` gates fields by version.
- Serialization writes `$version`; deserialization treats a missing `$version` as version `1`.
- Required constructor parameters must remain persisted across all supported versions. If a field is introduced later, use a default value, nullability, or a mutable/no-arg restoration path.

## Cyclic Graphs And Limitations

- Serialization uses identity-based tracking (`IdentityHashMap`) for persistable objects.
- Deserialization keeps an `id -> object` registry and resolves `$ref` through it.
- Cycles are restored only when the involved class can be pre-created before field population, which typically means an accessible no-arg constructor and mutable fields.
- Constructor-only cyclic classes fail with a clear `DeserializationException` explaining why the graph cannot be restored safely.
- Filtering remains JSON-tree-based; arrays and object-to-object comparisons are intentionally not supported in filter expressions.

## API Overview

```kotlin
val serializer = KPersist.serializer()
val json = serializer.serialize(user)
val oldJson = serializer.serialize(user, 1)

val restored = JsonDeserializer(User::class, json).instance()

val stream = KPersist.stream(User::class)
    .add(json)
    .toList(Filters.eq("address.city", "Novosibirsk"))

val session = KPersist.session(Path.of("data"))
session.insert(user).persist()
val activeUsers = session.find(User::class, Filters.eq("is_active", true))
session.delete(User::class, Filters.eq("id", "user-2")).persist()
```

Extended overloads for explicit document versions are intentionally kept on serializer, stream, and session for backward-compatible reads and writes.

## Demo

Runnable sample domain classes live under `src/main/kotlin/ru/nsu/demo`:

- `DemoUser`
- `DemoAddress`
- `DemoOrder`
- `DemoOrderItem`
- `DemoScenario.kt`

The demo shows:

- versioned serialization of a user;
- `insert -> persist -> find(filter) -> delete(filter) -> persist`;
- the on-disk layout under `build/demo-session/kpersist`.

## Project Structure

```text
src/main/kotlin/ru/nsu
  api/         Public interfaces
  annotation/  Persistence annotations
  codec/       JSON encoding/decoding and object restoration
  metadata/    Reflection metadata inspection
  query/       Raw JSON filters
  storage/     Session and file-backed document store
  demo/        Runnable example scenario
src/test/kotlin/ru/nsu
  codec/       Serialization/deserialization tests
  integration/ Session, versioning, cyclic graph, and audit tests
  query/       Filter tests
  compat/      Backward-compatibility alias tests
```

## Running Checks

```bash
./gradlew test
```
