package org.ost.marketplace.common;

public interface Identifiable {

    Long getId();

    default boolean isNew() {
        return getId() == null;
    }
}
