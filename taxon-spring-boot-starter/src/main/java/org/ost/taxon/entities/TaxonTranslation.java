package org.ost.taxon.entities;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.relational.core.mapping.Table;

/** A single locale's name/description for one {@link Taxon}, keyed by {@code (taxonId, locale)}. */
@Value
@Builder
@FieldNameConstants
@Table("taxon_translation")
public class TaxonTranslation {

    Long   taxonId;
    String locale;
    String name;
    String description;
}
