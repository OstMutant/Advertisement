package org.ost.integrationtests.support;

import lombok.NonNull;
import org.ost.platform.taxon.spi.TaxonPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.provider.repository.ProviderProfileRepository;
import org.ost.provider.services.ProviderProfileService;

/** Shared mock-wiring for ProviderProfileService's Mockito-based (non-Spring-context) tests. */
public final class ProviderProfileServiceTestSupport {

    private ProviderProfileServiceTestSupport() {
    }

    public static ProviderProfileService newService(@NonNull ProviderProfileRepository repository,
                                                      @NonNull ComponentFactory<TaxonPort> taxonPortFactory) {
        return new ProviderProfileService(repository, taxonPortFactory);
    }
}
