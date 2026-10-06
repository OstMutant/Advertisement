package org.ost.orchestrator.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.spi.AdvertisementPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.spi.ProviderProfilePort;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Plain Mockito unit test for {@link AdvertisementOwnerProfileLookupService}. */
@ExtendWith(MockitoExtension.class)
class AdvertisementOwnerProfileLookupServiceTest {

    @Mock private ComponentFactory<AdvertisementPort> advertisementPortFactory;
    @Mock private AdvertisementPort advertisementPort;
    @Mock private ComponentFactory<ProviderProfilePort> providerProfilePortFactory;
    @Mock private ProviderProfilePort providerProfilePort;

    private AdvertisementOwnerProfileLookupService service;

    @BeforeEach
    void setUp() {
        service = new AdvertisementOwnerProfileLookupService(advertisementPortFactory, providerProfilePortFactory);
        lenient().when(advertisementPortFactory.findIfAvailable()).thenReturn(Optional.of(advertisementPort));
        lenient().when(providerProfilePortFactory.findIfAvailable()).thenReturn(Optional.of(providerProfilePort));
    }

    @Test
    void findOwnerProfileId_ownerHasProfile_returnsProfileId() {
        when(advertisementPort.findById(10L)).thenReturn(Optional.of(
                AdvertisementInfoDto.builder().id(10L).createdBy(42L).build()));
        when(providerProfilePort.findByActorId(42L)).thenReturn(Optional.of(
                ProviderProfileDto.builder().id(99L).actorId(42L).build()));

        assertThat(service.findOwnerProfileId(10L)).contains(99L);
    }

    @Test
    void findOwnerProfileId_ownerHasNoProfile_returnsEmpty() {
        when(advertisementPort.findById(10L)).thenReturn(Optional.of(
                AdvertisementInfoDto.builder().id(10L).createdBy(42L).build()));
        when(providerProfilePort.findByActorId(42L)).thenReturn(Optional.empty());

        assertThat(service.findOwnerProfileId(10L)).isEmpty();
    }

    @Test
    void findOwnerProfileId_advertisementNotFound_returnsEmpty() {
        when(advertisementPort.findById(10L)).thenReturn(Optional.empty());

        assertThat(service.findOwnerProfileId(10L)).isEmpty();
    }

    @Test
    void findOwnerProfileId_advertisementStarterAbsent_returnsEmpty() {
        when(advertisementPortFactory.findIfAvailable()).thenReturn(Optional.empty());

        assertThat(service.findOwnerProfileId(10L)).isEmpty();
    }
}
