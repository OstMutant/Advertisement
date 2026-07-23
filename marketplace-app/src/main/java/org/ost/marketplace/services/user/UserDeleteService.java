package org.ost.marketplace.services.user;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.advertisement.AdvertisementSaveService;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.spi.AdvertisementPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.user.spi.UserPort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDeleteService {

    private final ComponentFactory<AdvertisementPort> advertisementPortFactory;
    private final AdvertisementSaveService             advertisementSaveService;
    private final UserPort                             userPort;

    // cascades to the user's own ads first -- avoids a created_by FK block on later retention purge
    public void delete(@NonNull Long userId, @NonNull Long actingUserId) {
        advertisementPortFactory.ifAvailable(port -> {
            for (AdvertisementInfoDto ad : port.findByCreator(userId)) {
                advertisementSaveService.delete(ad.getId(), actingUserId, ad.getVersion());
            }
        });
        userPort.delete(userId, actingUserId);
    }
}
