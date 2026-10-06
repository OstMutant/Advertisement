package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.spi.AdvertisementPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.spi.ProviderProfilePort;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Resolves an advertisement's owner's provider profile id -- {@link ContactAccessService}'s collaborator for the ADVERTISEMENT-to-PROVIDER_PROFILE contact fallback. */
@Service
@RequiredArgsConstructor
public class AdvertisementOwnerProfileLookupService {

    private final ComponentFactory<AdvertisementPort> advertisementPortFactory;
    private final ComponentFactory<ProviderProfilePort> providerProfilePortFactory;

    public Optional<Long> findOwnerProfileId(@NonNull Long advertisementId) {
        return advertisementPortFactory.findIfAvailable()
                .flatMap(port -> port.findById(advertisementId))
                .map(AdvertisementInfoDto::getOwnerUserId)
                .flatMap(this::findProfileIdByActorId);
    }

    private Optional<Long> findProfileIdByActorId(Long actorId) {
        return providerProfilePortFactory.findIfAvailable()
                .flatMap(port -> port.findByActorId(actorId))
                .map(ProviderProfileDto::getId);
    }
}
