package ru.nsu.integration;

import ru.nsu.annotation.PersistField;
import ru.nsu.annotation.Persistable;

@Persistable
public class JavaProfile {
    @PersistField
    public String id;

    @PersistField(name = "lvl")
    public Integer level;

    public transient String secret;

    public JavaProfile() {
    }

    public String getLabel() {
        return id + ":" + level;
    }
}
