package org.ost.orchestrator.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.platform.advertisement.spi.AdvertisementPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.providerprofile.spi.ProviderProfilePort;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Plain Mockito unit test for {@link UserPurgeEligibilityService}'s referential-integrity checks. */
@ExtendWith(MockitoExtension.class)
class UserPurgeEligibilityServiceTest {

    @Mock private ComponentFactory<AdvertisementPort> advertisementPortFactory;
    @Mock private AdvertisementPort advertisementPort;
    @Mock private ComponentFactory<ProviderProfilePort> providerProfilePortFactory;
    @Mock private ProviderProfilePort providerProfilePort;

    private UserPurgeEligibilityService service;

    @BeforeEach
    void setUp() {
        service = new UserPurgeEligibilityService(advertisementPortFactory, providerProfilePortFactory);
    }

    private void stubAdvertisementPortAvailable() {
        lenient().when(advertisementPortFactory.findIfAvailable()).thenReturn(Optional.of(advertisementPort));
        lenient().doAnswer(inv -> {
            Consumer<AdvertisementPort> consumer = inv.getArgument(0);
            consumer.accept(advertisementPort);
            return null;
        }).when(advertisementPortFactory).ifAvailable(any());
    }

    private void stubProviderProfilePortAvailable() {
        lenient().when(providerProfilePortFactory.findIfAvailable()).thenReturn(Optional.of(providerProfilePort));
    }

    @Test
    void findStillReferencedIds_noneReferenced_returnsEmpty() {
        stubAdvertisementPortAvailable();
        when(advertisementPort.findOwnerIds(Set.of(1L, 2L, 3L))).thenReturn(Set.of());

        assertThat(service.findStillReferencedIds(Set.of(1L, 2L, 3L))).isEmpty();
    }

    @Test
    void findStillReferencedIds_stillOwnsAdvertisement_isReported() {
        stubAdvertisementPortAvailable();
        when(advertisementPort.findOwnerIds(Set.of(1L, 2L, 3L))).thenReturn(Set.of(2L));

        assertThat(service.findStillReferencedIds(Set.of(1L, 2L, 3L))).containsExactly(2L);
    }

    @Test
    void findStillReferencedIds_stillOwnsProviderProfile_isReported() {
        stubProviderProfilePortAvailable();
        when(providerProfilePort.findOwnerIds(Set.of(1L, 2L, 3L))).thenReturn(Set.of(2L));

        assertThat(service.findStillReferencedIds(Set.of(1L, 2L, 3L))).containsExactly(2L);
    }

    @Test
    void findStillReferencedIds_bothPortsAbsent_returnsEmpty() {
        assertThat(service.findStillReferencedIds(Set.of(1L, 2L))).isEmpty();
    }

    @Test
    void clearAdvertisementReferences_delegatesToPort() {
        stubAdvertisementPortAvailable();

        service.clearAdvertisementReferences(Set.of(1L, 2L));

        verify(advertisementPort).clearActorReferences(Set.of(1L, 2L));
    }

    @Test
    void clearAdvertisementReferences_advertisementStarterAbsent_doesNothing() {
        // advertisementPortFactory.ifAvailable(...) left unstubbed -- ObjectProvider-absent shape.
        service.clearAdvertisementReferences(Set.of(1L, 2L));

        verify(advertisementPort, never()).clearActorReferences(any());
    }
}
