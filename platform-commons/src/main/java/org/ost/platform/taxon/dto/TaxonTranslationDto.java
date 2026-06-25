package org.ost.platform.taxon.dto;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class TaxonTranslationDto {
    @NonNull String locale;
    @NonNull String name;
    @NonNull String description;
}
