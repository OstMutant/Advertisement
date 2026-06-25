package org.ost.marketplace.ui.dto;

public interface Identifiable {

    Long getId();

    default boolean isNew() {
        return getId() == null;
    }
}
