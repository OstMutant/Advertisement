package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.advertisement.spi.AdvertisementPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.providerprofile.spi.ProviderProfilePort;
import org.ost.platform.taxon.spi.TaxonPort;
import org.ost.platform.user.spi.UserPort;
import org.springframework.stereotype.Service;

import java.util.Set;

// Named exception to the orchestrator's <=2-port rule: pure per-EntityType routing, no cross-port composition.
@Service
@RequiredArgsConstructor
public class EntityExistenceService {

    private final ComponentFactory<AdvertisementPort>   advertisementPortFactory;
    private final ComponentFactory<UserPort>            userPortFactory;
    private final ComponentFactory<TaxonPort>           taxonPortFactory;
    private final ComponentFactory<ProviderProfilePort> providerProfilePortFactory;

    public Set<Long> findExisting(@NonNull EntityType entityType, @NonNull Set<Long> entityIds) {
        return switch (entityType) {
            case ADVERTISEMENT       -> advertisementPortFactory.findIfAvailable()
                    .map(p -> p.findExistingIds(entityIds))
                    .orElse(Set.of());
            case USER, USER_SETTINGS -> userPortFactory.findIfAvailable()
                    .map(p -> p.findExistingIds(entityIds))
                    .orElse(Set.of());
            case TAXON               -> taxonPortFactory.findIfAvailable()
                    .map(p -> p.findExistingIds(entityIds))
                    .orElse(Set.of());
            case PROVIDER_PROFILE    -> providerProfilePortFactory.findIfAvailable()
                    .map(p -> p.findExistingIds(entityIds))
                    .orElse(Set.of());
        };
    }
}
