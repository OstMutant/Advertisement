package org.ost.taxon.repository;

import lombok.experimental.FieldNameConstants;

/** Filter conditions for {@link TaxonRepository}'s catalog queries: whether soft-deleted rows are included. */
@FieldNameConstants
public record TaxonFilter(boolean showDeleted) {

    public static TaxonFilter of(boolean showDeleted) {
        return new TaxonFilter(showDeleted);
    }

    public static TaxonFilter active() {
        return new TaxonFilter(false);
    }

    public static TaxonFilter all() {
        return new TaxonFilter(true);
    }
}
