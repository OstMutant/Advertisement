package org.ost.advertisement.ui.views.advertisements.overlay;

import org.ost.advertisement.dto.AdvertisementInfoDto;

// Immutable per-open context; replaced atomically on each open/transition.
public record OverlaySession(
        Mode mode,
        AdvertisementInfoDto ad,   // null for CREATE
        Runnable onSaved,
        boolean enteredFromView
) {
    static OverlaySession forView(AdvertisementInfoDto ad, Runnable onSaved) {
        return new OverlaySession(Mode.VIEW, ad, onSaved, false);
    }

    static OverlaySession forCreate(Runnable onSaved) {
        return new OverlaySession(Mode.CREATE, null, onSaved, false);
    }

    static OverlaySession forEdit(AdvertisementInfoDto ad, Runnable onSaved) {
        return new OverlaySession(Mode.EDIT, ad, onSaved, false);
    }

    OverlaySession toView() {
        return new OverlaySession(Mode.VIEW, ad, onSaved, false);
    }

    OverlaySession toEdit() {
        return new OverlaySession(Mode.EDIT, ad, onSaved, true);
    }
}