package org.ost.taxon.entities;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.relational.core.mapping.Table;

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
