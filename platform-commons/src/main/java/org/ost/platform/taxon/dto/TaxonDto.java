package org.ost.platform.taxon.dto;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.taxon.model.TaxonType;

/** A single localised taxon entry (category, tag, or classifier) as returned to callers. */
@Value
@Builder
@FieldNameConstants
public class TaxonDto {
    @NonNull Long      id;
    @NonNull TaxonType type;
    String             code;
    @NonNull String    name;
    @NonNull String    description;
    boolean            deleted;
    Long               version;
}
