package org.ost.advertisement.ui.dto;

import java.util.Objects;

public interface EditDto {

    Long getId();

    default boolean isNew() {
        return Objects.isNull(getId());
    }
}
