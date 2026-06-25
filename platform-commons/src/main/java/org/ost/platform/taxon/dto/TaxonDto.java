package org.ost.platform.taxon.dto;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import org.ost.platform.taxon.model.TaxonType;

@Value
@Builder
public class TaxonDto {
    @NonNull Long      id;
    @NonNull TaxonType type;
    String             code;
    @NonNull String    name;
    @NonNull String    description;
    boolean            deleted;
}
