package org.ost.integrationtests.level1.promotion;

import org.junit.jupiter.api.Test;
import org.ost.platform.promotion.model.PromotionReason;
import org.ost.platform.providerprofile.dto.ProviderProfileScope;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain unit test (no Spring context, no DB) — the ranks that order promoted entries in a list, and the scope that restricts and pins nothing. */
class PromotionReasonTest {

    @Test
    void ranks_areUniqueAcrossAllReasons() {
        long distinct = Arrays.stream(PromotionReason.values()).map(PromotionReason::rank).collect(Collectors.toSet()).size();

        assertThat(distinct).isEqualTo(PromotionReason.values().length);
    }

    @Test
    void topRated_hasRankOne() {
        assertThat(PromotionReason.TOP_RATED.rank()).isEqualTo(1);
    }

    @Test
    void allScope_restrictsNothingAndPinsNothing() {
        assertThat(ProviderProfileScope.ALL.onlyIds()).isNull();
        assertThat(ProviderProfileScope.ALL.pinnedGroups()).isEmpty();
        assertThat(ProviderProfileScope.ALL.pinnedAfterRest()).isFalse();
    }
}
