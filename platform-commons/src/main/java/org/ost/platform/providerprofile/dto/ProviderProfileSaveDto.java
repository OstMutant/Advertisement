package org.ost.platform.providerprofile.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.ost.platform.providerprofile.model.ProviderKind;

import java.util.Set;

public record ProviderProfileSaveDto(
        Long id,
        @NotNull ProviderKind kind,
        @Size(max = ABOUT_RAW_MAX_LENGTH) String about,
        @Size(max = CATEGORY_MAX_COUNT) Set<Long> categoryIds,
        Long cityTaxonId,
        Long version
) {
    public static final int ABOUT_MAX_LENGTH     = 2000;
    public static final int ABOUT_RAW_MAX_LENGTH = 20_000;
    public static final int CATEGORY_MAX_COUNT   = 10;
}
