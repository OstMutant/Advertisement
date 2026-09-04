package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.spi.AdvertisementPort;
import org.ost.platform.apikey.spi.ApiKeyPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.user.spi.UserAccountPort;
import org.springframework.stereotype.Service;

/**
 * Application-level use case: cascade-delete a user's own dependent data (advertisements, provider
 * profile, API keys) before deleting the account itself, gated by self-or-admin authorization. 2
 * direct domain ports (Advertisement, ApiKey) plus the {@link ProviderProfileSaveService}
 * collaborator and the mandatory {@link UserAccountPort} dependency.
 */
@Service
@RequiredArgsConstructor
public class UserDeleteService {

    private final ComponentFactory<AdvertisementPort>   advertisementPortFactory;
    private final ComponentFactory<ApiKeyPort>           apiKeyPortFactory;
    private final AdvertisementSaveService              advertisementSaveService;
    private final ProviderProfileSaveService             providerProfileSaveService;
    private final UserAccountPort                       accountPort;
    private final AuthorizationService                  authorizationService;

    // cascades to the user's own ads, provider profile, and API keys first -- avoids an FK block on later retention purge
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
        apiKeyPortFactory.ifAvailable(port -> port.deleteAllForActor(userId));
        accountPort.delete(userId, actingUserId);
    }
}
