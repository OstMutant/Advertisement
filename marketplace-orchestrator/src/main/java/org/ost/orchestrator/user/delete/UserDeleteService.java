package org.ost.orchestrator.user.delete;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.advertisement.save.AdvertisementSaveService;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.spi.AdvertisementPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.providerprofile.spi.ProviderProfilePort;
import org.ost.platform.user.spi.UserAccountPort;
import org.springframework.stereotype.Service;

/**
 * Application-level use case: cascade-delete a user's own dependent data (advertisements, provider
 * profile) before deleting the account itself. 2 direct domain ports (Advertisement +
 * ProviderProfile) plus the mandatory {@link UserAccountPort} dependency.
 */
@Service
@RequiredArgsConstructor
public class UserDeleteService {

    private final ComponentFactory<AdvertisementPort>   advertisementPortFactory;
    private final ComponentFactory<ProviderProfilePort> providerProfilePortFactory;
    private final AdvertisementSaveService              advertisementSaveService;
    private final UserAccountPort                       accountPort;

    // cascades to the user's own ads and provider profile first -- avoids an FK block on later retention purge
    public void delete(@NonNull Long userId, @NonNull Long actingUserId) {
        advertisementPortFactory.ifAvailable(port -> {
            for (AdvertisementInfoDto ad : port.findByCreator(userId)) {
                advertisementSaveService.delete(ad.getId(), actingUserId, ad.getVersion());
            }
        });
        providerProfilePortFactory.ifAvailable(port -> port.findByActorId(userId)
                .ifPresent(profile -> port.delete(profile.getId(), profile.getVersion())));
        accountPort.delete(userId, actingUserId);
    }
}
