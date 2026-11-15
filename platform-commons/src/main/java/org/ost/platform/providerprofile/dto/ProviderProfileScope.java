package org.ost.platform.providerprofile.dto;

import lombok.NonNull;

import java.util.List;
import java.util.Set;

/** Narrows and orders a provider profile list by ids decided outside the owning domain: {@code onlyIds} (null means no restriction) limits it, {@code pinnedGroups} are listed group by group before every other entry, or after them when {@code pinnedAfterRest} is set. */
public record ProviderProfileScope(Set<Long> onlyIds, @NonNull List<Set<Long>> pinnedGroups, boolean pinnedAfterRest) {

    public static final ProviderProfileScope ALL = new ProviderProfileScope(null, List.of(), false);
}
