package org.ost.integrationtests.support;

import lombok.NonNull;
import org.ost.advertisement.repository.AdvertisementRepository;
import org.ost.advertisement.services.AdvertisementEnrichmentService;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.platform.attachment.spi.AttachmentPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.taxon.spi.TaxonPort;
import org.ost.platform.user.spi.UserPort;

/** Shared mock-wiring for AdvertisementService's Mockito-based (non-Spring-context) tests. */
public final class AdvertisementServiceTestSupport {

    private AdvertisementServiceTestSupport() {
    }

    public static AdvertisementService newService(@NonNull AdvertisementRepository repository,
                                                   @NonNull ComponentFactory<AttachmentPort> attachmentPortFactory,
                                                   @NonNull ComponentFactory<TaxonPort> taxonPortFactory,
                                                   @NonNull ComponentFactory<UserPort> userPortFactory) {
        AdvertisementEnrichmentService enrichmentService =
                new AdvertisementEnrichmentService(attachmentPortFactory, taxonPortFactory, userPortFactory);
        return new AdvertisementService(repository, attachmentPortFactory, taxonPortFactory, enrichmentService);
    }
}
