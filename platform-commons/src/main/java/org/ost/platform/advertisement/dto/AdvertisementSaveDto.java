package org.ost.platform.advertisement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record AdvertisementSaveDto(
        Long id,
        @NotBlank @Size(min = 1, max = TITLE_MAX_LENGTH) String title,
        @NotBlank @Size(max = DESCRIPTION_RAW_MAX_LENGTH) String description,
        @Size(max = CATEGORY_MAX_COUNT) Set<Long> categoryIds,
        Long version
) {
    public static final int TITLE_MAX_LENGTH           = 255;
    public static final int DESCRIPTION_MAX_LENGTH     = 2000;
    public static final int DESCRIPTION_RAW_MAX_LENGTH = 20_000;
    public static final int CATEGORY_MAX_COUNT         = 10;
}
