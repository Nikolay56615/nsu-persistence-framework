# Requirements Clarification

This file captures the final project boundaries implemented in the framework.

## Persistence Model

- Only classes annotated with `@Persistable` are serialized as entity objects.
- Only fields annotated with `@PersistField` participate in persistence.
- The project intentionally serializes data, not behavior:
  - computed properties are excluded;
  - methods are excluded;
  - transient or unannotated state is excluded.

## Annotation Decision

- The final contract is `@Persistable` + `@PersistField(name, since, until)`.
- Field aliasing and versioning must be expressed through `@PersistField`.

## JSON And Identity

- JSON is produced manually through a tree codec; the framework does not delegate whole-object serialization to Jackson.
- Persistable objects include framework metadata:
  - `$id` for first appearance of an object instance;
  - `$ref` for repeated appearance of the same instance;
  - `$version` for document version.
- Deserialization uses a two-phase restore path when the class can be pre-created and populated later.

## Versioning

- A class declares its maximum supported version with `@Persistable(version = N)`.
- A field declares its active version range with `@PersistField(since, until)`.
- Missing `$version` is interpreted as version `1`.
- Required constructor fields must remain available across all supported versions; otherwise the class must provide defaults, nullability, or a mutable/no-arg restore path.

## Partial Loading

- Filters are evaluated against raw `JsonNode` trees before object construction.
- Supported composition: `AND`, `OR`, `NOT`.
- Supported paths: nested object paths like `address.city`.
- Unsupported on purpose:
  - array indexing in paths;
  - comparisons against whole objects or arrays.

## Storage

- Session storage layout is `<base>/kpersist/<fqcn>/<uuid>.json`.
- `insert(...)` and `delete(...)` are buffered and applied on `persist()`.
- `find(...)` reflects both persisted documents and pending insert/delete operations.

## Known Limitation

- Cyclic graphs require participating classes to support pre-creation before field population.
- In practice this means constructor-only immutable cycles are rejected with a clear error instead of being restored incorrectly.
