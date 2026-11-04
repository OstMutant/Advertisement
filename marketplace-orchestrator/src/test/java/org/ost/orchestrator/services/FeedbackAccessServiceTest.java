package org.ost.orchestrator.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.spi.FeedbackPort;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Unit tests for {@link FeedbackAccessService#count}. */
@ExtendWith(MockitoExtension.class)
class FeedbackAccessServiceTest {

    private static final Long PROFILE_ID = 7L;

    @Mock private TransactionTemplate tx;
    @Mock private ComponentFactory<FeedbackPort> feedbackPortFactory;
    @Mock private ComponentFactory<AuditPort> auditPortFactory;
    @Mock private UserActorNameService userActorNameService;
    @Mock private AuthorizationService authorizationService;
    @Mock private FeedbackPort feedbackPort;

    private FeedbackAccessService service;

    @BeforeEach
    void setUp() {
        service = new FeedbackAccessService(tx, feedbackPortFactory, auditPortFactory, userActorNameService, authorizationService);
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
}
