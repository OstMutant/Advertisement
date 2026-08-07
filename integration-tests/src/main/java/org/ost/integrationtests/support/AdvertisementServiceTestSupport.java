package org.ost.integrationtests.support;

import lombok.NonNull;
import org.ost.advertisement.repository.AdvertisementRepository;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.taxon.spi.TaxonPort;

/** Shared mock-wiring for AdvertisementService's Mockito-based (non-Spring-context) tests. */
public final class AdvertisementServiceTestSupport {

    private AdvertisementServiceTestSupport() {
    }

    public static AdvertisementService newService(@NonNull AdvertisementRepository repository,
                                                   @NonNull ComponentFactory<TaxonPort> taxonPortFactory) {
        return new AdvertisementService(repository, taxonPortFactory);
    }
}
