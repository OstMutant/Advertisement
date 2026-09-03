package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.spi.AdvertisementPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.user.spi.UserAccountPort;
import org.springframework.stereotype.Service;

/**
 * Application-level use case: cascade-delete a user's own dependent data (advertisements, provider
 * profile) before deleting the account itself, gated by self-or-admin authorization. 1 direct
 * domain port (Advertisement) plus the {@link ProviderProfileSaveService} collaborator and the
 * mandatory {@link UserAccountPort} dependency.
 */
@Service
@RequiredArgsConstructor
public class UserDeleteService {

    private final ComponentFactory<AdvertisementPort>   advertisementPortFactory;
    private final AdvertisementSaveService              advertisementSaveService;
    private final ProviderProfileSaveService             providerProfileSaveService;
    private final UserAccountPort                       accountPort;
    private final AuthorizationService                  authorizationService;

    // cascades to the user's own ads and provider profile first -- avoids an FK block on later retention purge
    public void delete(@NonNull Long userId, @NonNull Long actingUserId) {
        authorizationService.requireCanEditAccount(actingUserId, userId);
        advertisementPortFactory.ifAvailable(port -> {
            for (AdvertisementInfoDto ad : port.findByCreator(userId)) {
                advertisementSaveService.delete(ad.getId(), actingUserId, ad.getVersion());
            }
        });
        if (providerProfileSaveService.isAvailable()) {
            providerProfileSaveService.findByActorId(userId)
                    .ifPresent(profile -> providerProfileSaveService.delete(profile.getId(), actingUserId, profile.getVersion()));
        }
        accountPort.delete(userId, actingUserId);
    }
}
