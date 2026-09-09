package org.ost.taxon.entities;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.taxon.model.TaxonType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/** A single catalog entry (category, city, ...) typed by {@link TaxonType}; soft-deletable via {@code deletedAt}/{@code deletedBy} and optimistically locked via {@code version}. */
@Value
@Builder
@FieldNameConstants
@Table("taxon")
public class Taxon {

    @Id
    Long      id;
    TaxonType type;
    String    code;
    Instant   deletedAt;
    Long      deletedBy;
    @CreatedDate
    Instant   createdAt;
    @LastModifiedDate
    Instant   updatedAt;
    Long      createdBy;
    Long      updatedBy;
    @Version
    Long      version;
}
