package org.ost.platform.promotion.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** Why an entry is lifted out of the catalog; each value has exactly one writer that owns its rows, and {@code rank} orders the reasons in a list (lower first). */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum PromotionReason {
    TOP_RATED(1);

    private final int rank;
}
