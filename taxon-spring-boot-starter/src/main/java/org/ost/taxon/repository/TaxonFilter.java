package org.ost.taxon.repository;

import lombok.experimental.FieldNameConstants;

/** Filter conditions for {@link TaxonRepository}'s catalog queries: an optional name search and whether soft-deleted rows are included. */
@FieldNameConstants
public record TaxonFilter(String name, boolean showDeleted) {

    public static TaxonFilter of(String name, boolean showDeleted) {
        return new TaxonFilter(name, showDeleted);
    }

    public static TaxonFilter active() {
        return new TaxonFilter(null, false);
    }

    public static TaxonFilter all() {
        return new TaxonFilter(null, true);
    }
}
