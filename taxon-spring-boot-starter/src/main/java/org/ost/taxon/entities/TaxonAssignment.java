package org.ost.taxon.entities;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/** A single row of the generic entity-to-taxon many-to-many table, keyed by {@code (entityType, entityId, taxonId)} rather than a domain-specific foreign key. */
@Value
@Builder
@FieldNameConstants
@Table("taxon_assignment")
public class TaxonAssignment {

    String  entityType;
    Long    entityId;
    Long    taxonId;
    Instant assignedAt;
    Long    assignedBy;
}
