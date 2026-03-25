package ru.nsu.integration;

import ru.nsu.annotation.PersistField;
import ru.nsu.annotation.Persistable;

@Persistable(version = 2)
public class JavaVersionedNode {
    @PersistField
    private String id;

    @PersistField(until = 1)
    private String alias;

    @PersistField(since = 2)
    private String note;

    @PersistField
    private JavaVersionedNode next;

    public JavaVersionedNode() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public JavaVersionedNode getNext() {
        return next;
    }

    public void setNext(JavaVersionedNode next) {
        this.next = next;
    }
}
