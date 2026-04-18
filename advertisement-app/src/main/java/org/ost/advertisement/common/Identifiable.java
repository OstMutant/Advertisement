package org.ost.advertisement.common;

public interface Identifiable {

    Long getId();

    default boolean isNew() {
        return getId() == null;
    }
}
