package org.ost.taxon.services;

import lombok.NonNull;

public record TaxonTranslationData(@NonNull String name, @NonNull String description) {}
