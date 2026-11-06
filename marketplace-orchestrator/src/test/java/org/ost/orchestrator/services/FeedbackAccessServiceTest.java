package org.ost.orchestrator.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.dto.FeedbackDto;
import org.ost.platform.feedback.dto.FeedbackSaveDto;
import org.ost.platform.feedback.spi.FeedbackPort;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link FeedbackAccessService}: a read falls back to an empty result when the feedback starter is absent. */
@ExtendWith(MockitoExtension.class)
class FeedbackAccessServiceTest {

    private static final Long PROFILE_ID = 7L;

    @Mock private TransactionTemplate tx;
    @Mock private ComponentFactory<FeedbackPort> feedbackPortFactory;
    @Mock private ComponentFactory<AuditPort> auditPortFactory;
    @Mock private UserActorNameService userActorNameService;
    @Mock private AuthorizationService authorizationService;
    @Mock private FeedbackPort feedbackPort;
    @Mock private PromotionRefreshService promotionRefreshService;

    private FeedbackAccessService service;

    @BeforeEach
    void setUp() {
        service = new FeedbackAccessService(tx, feedbackPortFactory, auditPortFactory, userActorNameService, authorizationService, promotionRefreshService);
    }

    @Test
    void count_portPresent_returnsPortCount() {
        when(feedbackPortFactory.findIfAvailable()).thenReturn(Optional.of(feedbackPort));
        when(feedbackPort.count(EntityType.PROVIDER_PROFILE, PROFILE_ID)).thenReturn(3);

        assertThat(service.count(EntityType.PROVIDER_PROFILE, PROFILE_ID)).isEqualTo(3);
    }

    @Test
    void count_portAbsent_returnsZero() {
        when(feedbackPortFactory.findIfAvailable()).thenReturn(Optional.empty());

        assertThat(service.count(EntityType.PROVIDER_PROFILE, PROFILE_ID)).isZero();
    }

    @Test
    void save_reportsTheChangeOfTheReviewedEntityToPromotion() {
        FeedbackSaveDto dto = new FeedbackSaveDto(null, EntityType.PROVIDER_PROFILE, PROFILE_ID, 10L, 5, "Great", null);
        FeedbackDto saved = org.mockito.Mockito.mock(FeedbackDto.class);
        when(saved.entityType()).thenReturn(EntityType.PROVIDER_PROFILE);
        when(saved.entityId()).thenReturn(PROFILE_ID);
        when(feedbackPortFactory.get()).thenReturn(feedbackPort);
        when(feedbackPort.save(dto)).thenReturn(saved);

        assertThat(service.save(dto)).isSameAs(saved);

        verify(promotionRefreshService).entityChanged(EntityType.PROVIDER_PROFILE, PROFILE_ID);
    }

    @Test
    void flagFeedback_reportsTheChangeOfTheReviewedEntityToPromotion() {
        Long feedbackId = 99L;
        FeedbackDto feedback = org.mockito.Mockito.mock(FeedbackDto.class);
        when(feedback.entityType()).thenReturn(EntityType.PROVIDER_PROFILE);
        when(feedback.entityId()).thenReturn(PROFILE_ID);
        when(feedbackPortFactory.get()).thenReturn(feedbackPort);
        when(feedbackPort.findFeedbackById(feedbackId)).thenReturn(Optional.of(feedback));

        service.flagFeedback(feedbackId, 10L, "Spam");

        verify(promotionRefreshService).entityChanged(EntityType.PROVIDER_PROFILE, PROFILE_ID);
    }
}
