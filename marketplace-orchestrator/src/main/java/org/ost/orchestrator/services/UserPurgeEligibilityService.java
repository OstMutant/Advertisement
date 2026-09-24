package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.platform.advertisement.spi.AdvertisementPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.providerprofile.spi.ProviderProfilePort;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/** Referential-integrity check for user retention purge: which candidate ids are still owned by an advertisement or provider profile. */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPurgeEligibilityService {

    private final ComponentFactory<AdvertisementPort>   advertisementPortFactory;
    private final ComponentFactory<ProviderProfilePort> providerProfilePortFactory;

    public void clearAdvertisementReferences(@NonNull Set<Long> candidateIds) {
        advertisementPortFactory.ifAvailable(p -> p.clearActorReferences(candidateIds));
    }

    public Set<Long> findStillReferencedIds(@NonNull Set<Long> candidateIds) {
        Set<Long> adOwnerIds = advertisementPortFactory.findIfAvailable()
                .map(p -> p.findOwnerIds(candidateIds))
                .orElse(Set.of());
        Set<Long> providerProfileOwnerIds = providerProfilePortFactory.findIfAvailable()
                .map(p -> p.findOwnerIds(candidateIds))
                .orElse(Set.of());

        Set<Long> stillReferenced = new HashSet<>();
        for (Long id : candidateIds) {
            if (adOwnerIds.contains(id)) {
                log.warn("Skipped purging user {} - still owns an advertisement, will retry next run", id);
                stillReferenced.add(id);
            } else if (providerProfileOwnerIds.contains(id)) {
                log.warn("Skipped purging user {} - still owns a provider profile, will retry next run", id);
                stillReferenced.add(id);
            }
        }
        return stillReferenced;
    }
}
