package ru.nsu;

import ru.nsu.annotation.PersistName;
import ru.nsu.annotation.Persistable;

@Persistable
public class JavaProfile {
    public String id;

    @PersistName("lvl")
    public Integer level;

    public transient String secret;

    public JavaProfile() {
    }

    public String getLabel() {
        return id + ":" + level;
    }
}
