package org.ost.orchestrator.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.promotion.model.PromotionReason;
import org.ost.platform.promotion.spi.PromotionPlacementPort;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link PromotionRefreshService}: rules of the entity type are applied, a failing rule is isolated, and a removal clears every reason. */
@ExtendWith(MockitoExtension.class)
class PromotionRefreshServiceTest {

    private static final Long PROFILE_ID = 7L;

    @Mock private ComponentFactory<PromotionPlacementPort> portFactory;
    @Mock private PromotionPlacementPort port;
    @Mock private PromotionRule profileRule;
    @Mock private PromotionRule otherTypeRule;

    private PromotionRefreshService service;

    @BeforeEach
    void setUp() {
        lenient().when(portFactory.findIfAvailable()).thenReturn(Optional.of(port));
        lenient().when(profileRule.entityType()).thenReturn(EntityType.PROVIDER_PROFILE);
        lenient().when(profileRule.reason()).thenReturn(PromotionReason.TOP_RATED);
        lenient().when(otherTypeRule.entityType()).thenReturn(EntityType.ADVERTISEMENT);
        service = new PromotionRefreshService(List.of(profileRule, otherTypeRule), portFactory);
    }

    @Test
    void entityChanged_ruleQualifies_addsTheEntityForThatReason() {
        when(profileRule.qualifies(PROFILE_ID)).thenReturn(true);

        service.entityChanged(EntityType.PROVIDER_PROFILE, PROFILE_ID);

        verify(port).addForReason(EntityType.PROVIDER_PROFILE, PromotionReason.TOP_RATED, PROFILE_ID);
    }

    @Test
    void entityChanged_ruleDoesNotQualify_removesTheEntityForThatReason() {
        when(profileRule.qualifies(PROFILE_ID)).thenReturn(false);

        service.entityChanged(EntityType.PROVIDER_PROFILE, PROFILE_ID);

        verify(port).removeForReason(EntityType.PROVIDER_PROFILE, PromotionReason.TOP_RATED, PROFILE_ID);
    }

    @Test
    void entityChanged_ignoresRulesOfAnotherEntityType() {
        service.entityChanged(EntityType.PROVIDER_PROFILE, PROFILE_ID);

        verify(otherTypeRule, never()).qualifies(any());
    }

    @Test
    void entityChanged_failingRule_isIsolatedAndNeverThrows() {
        PromotionRule second = org.mockito.Mockito.mock(PromotionRule.class);
        when(second.entityType()).thenReturn(EntityType.PROVIDER_PROFILE);
        when(second.reason()).thenReturn(PromotionReason.TOP_RATED);
        when(second.qualifies(PROFILE_ID)).thenReturn(true);
        when(profileRule.qualifies(PROFILE_ID)).thenThrow(new IllegalStateException("boom"));
        PromotionRefreshService twoRules = new PromotionRefreshService(List.of(profileRule, second), portFactory);

        assertThatCode(() -> twoRules.entityChanged(EntityType.PROVIDER_PROFILE, PROFILE_ID)).doesNotThrowAnyException();

        verify(port).addForReason(EntityType.PROVIDER_PROFILE, PromotionReason.TOP_RATED, PROFILE_ID);
    }

    @Test
    void entityChanged_starterAbsent_doesNothing() {
        when(portFactory.findIfAvailable()).thenReturn(Optional.empty());

        assertThatCode(() -> service.entityChanged(EntityType.PROVIDER_PROFILE, PROFILE_ID)).doesNotThrowAnyException();

        verify(profileRule, never()).qualifies(any());
    }

    @Test
    void entityRemoved_removesTheEntityForEveryReason() {
        service.entityRemoved(EntityType.PROVIDER_PROFILE, PROFILE_ID);

        for (PromotionReason reason : PromotionReason.values()) {
            verify(port).removeForReason(EntityType.PROVIDER_PROFILE, reason, PROFILE_ID);
        }
    }

    @Test
    void entityRemoved_failure_reachesTheCaller() {
        doThrow(new IllegalStateException("db down")).when(port).removeForReason(any(), any(), any());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.entityRemoved(EntityType.PROVIDER_PROFILE, PROFILE_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recountAll_replacesEachRulesRowsWithItsQualifyingIds() {
        when(profileRule.qualifyingIds()).thenReturn(Set.of(1L, 2L));
        when(otherTypeRule.qualifyingIds()).thenReturn(Set.of());
        when(otherTypeRule.reason()).thenReturn(PromotionReason.TOP_RATED);

        service.recountAll();

        verify(port).replaceForReason(EntityType.PROVIDER_PROFILE, PromotionReason.TOP_RATED, Set.of(1L, 2L));
        verify(port).replaceForReason(EntityType.ADVERTISEMENT, PromotionReason.TOP_RATED, Set.of());
    }

    @Test
    void recountOnStartup_failure_isSwallowed() {
        when(profileRule.qualifyingIds()).thenThrow(new IllegalStateException("db not ready"));

        assertThatCode(() -> service.recountOnStartup()).doesNotThrowAnyException();
    }
}
