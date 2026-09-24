package org.ost.orchestrator.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.platform.user.spi.UserAccountPort;
import org.ost.platform.user.spi.UserPort;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Plain Mockito unit test for {@link UserCleanupService}'s purge orchestration. */
@ExtendWith(MockitoExtension.class)
class UserCleanupServiceTest {

    @Mock private UserPort userPort;
    @Mock private UserAccountPort accountPort;
    @Mock private UserPurgeEligibilityService eligibilityService;

    private UserCleanupService service;

    @BeforeEach
    void setUp() {
        service = new UserCleanupService(userPort, accountPort, eligibilityService);
    }

    @Test
    void cleanup_noCandidates_doesNothing() {
        when(userPort.findIdsDeletedOlderThan(90)).thenReturn(Set.of());

        service.cleanup(90);

        verify(eligibilityService, never()).clearAdvertisementReferences(any());
        verify(accountPort, never()).purge(any());
    }

    @Test
    void cleanup_allEligible_purgesAllCandidates() {
        when(userPort.findIdsDeletedOlderThan(90)).thenReturn(Set.of(1L, 2L, 3L));
        when(eligibilityService.findStillReferencedIds(Set.of(1L, 2L, 3L))).thenReturn(Set.of());

        service.cleanup(90);

        verify(eligibilityService).clearAdvertisementReferences(Set.of(1L, 2L, 3L));
        verify(accountPort).purge(Set.of(1L, 2L, 3L));
    }

    @Test
    void cleanup_someStillReferenced_purgesOnlyTheRest() {
        when(userPort.findIdsDeletedOlderThan(90)).thenReturn(Set.of(1L, 2L, 3L));
        when(eligibilityService.findStillReferencedIds(Set.of(1L, 2L, 3L))).thenReturn(Set.of(2L));

        service.cleanup(90);

        verify(accountPort).purge(Set.of(1L, 3L));
    }

    @Test
    void cleanup_allStillReferenced_neverCallsPurge() {
        when(userPort.findIdsDeletedOlderThan(90)).thenReturn(Set.of(1L, 2L));
        when(eligibilityService.findStillReferencedIds(Set.of(1L, 2L))).thenReturn(Set.of(1L, 2L));

        service.cleanup(90);

        verify(accountPort, never()).purge(any());
    }
}
