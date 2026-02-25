package org.ost.advertisement.ui.views.advertisements.overlay;

import org.ost.advertisement.dto.AdvertisementInfoDto;

public record OverlaySession(
        Mode mode,
        AdvertisementInfoDto ad,
        Runnable onSaved,
        boolean enteredFromView
) {
    OverlaySession toView() {
        return new OverlaySession(Mode.VIEW, ad, onSaved, false);
    }

    OverlaySession toEdit() {
        return new OverlaySession(Mode.EDIT, ad, onSaved, true);
    }
}