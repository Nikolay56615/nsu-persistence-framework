package ru.nsu.integration;

import ru.nsu.annotation.PersistField;
import ru.nsu.annotation.Persistable;

@Persistable(version = 2)
public class JavaVersionedAddress {
    @PersistField
    private final String city;

    @PersistField(name = "zip_code", since = 2)
    private final String zipCode;

    public JavaVersionedAddress(String city, String zipCode) {
        this.city = city;
        this.zipCode = zipCode;
    }

    public String getCity() {
        return city;
    }

    public String getZipCode() {
        return zipCode;
    }
}
