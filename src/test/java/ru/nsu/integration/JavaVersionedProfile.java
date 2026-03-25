package ru.nsu.integration;

import ru.nsu.annotation.PersistField;
import ru.nsu.annotation.Persistable;

@Persistable(version = 2)
public class JavaVersionedProfile {
    @PersistField
    private final String id;

    @PersistField(until = 1)
    private final String alias;

    @PersistField(since = 2)
    private final String email;

    @PersistField
    private final JavaVersionedAddress address;

    public JavaVersionedProfile(String id, String alias, String email, JavaVersionedAddress address) {
        this.id = id;
        this.alias = alias;
        this.email = email;
        this.address = address;
    }

    public String getId() {
        return id;
    }

    public String getAlias() {
        return alias;
    }

    public String getEmail() {
        return email;
    }

    public JavaVersionedAddress getAddress() {
        return address;
    }
}
