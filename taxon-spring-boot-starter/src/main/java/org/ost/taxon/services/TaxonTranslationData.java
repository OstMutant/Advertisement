package org.ost.taxon.services;

import lombok.NonNull;

/** One locale's name/description pair, passed from {@link DefaultTaxonPort} to {@link TaxonService} before being mapped into {@link org.ost.taxon.entities.TaxonTranslation} rows. */
public record TaxonTranslationData(@NonNull String name, @NonNull String description) {}
