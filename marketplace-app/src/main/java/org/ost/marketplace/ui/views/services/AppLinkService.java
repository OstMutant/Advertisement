package org.ost.marketplace.ui.views.services;

import lombok.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AppLinkService {

    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    public String advertisementUrl(@NonNull Long adId) {
        return publicBaseUrl + "/ads/" + adId;
    }
}
