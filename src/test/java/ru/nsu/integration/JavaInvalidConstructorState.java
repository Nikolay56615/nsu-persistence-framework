package ru.nsu.integration;

import ru.nsu.annotation.PersistField;
import ru.nsu.annotation.Persistable;

@Persistable
public class JavaInvalidConstructorState {
    @PersistField
    private final String id;

    private final String internalCode;

    public JavaInvalidConstructorState(String id, String internalCode) {
        this.id = id;
        this.internalCode = internalCode;
    }

    public String getId() {
        return id;
    }

    public String getInternalCode() {
        return internalCode;
    }
}
