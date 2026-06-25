package org.ost.marketplace.ui.views.components.overlay;

import org.ost.marketplace.ui.dto.EditDto;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;

public abstract class AbstractFormOverlayModeHandler<D extends EditDto> implements OverlayModeHandler {

    protected OverlayFormBinder<D> binder;

    public boolean hasChanges() {
        return binder != null && binder.hasChanges();
    }

    protected static void wireSaveGuard(UiPrimaryButton saveBtn, Runnable onSave) {
        saveBtn.addClickListener(_ -> {
            saveBtn.setEnabled(false);
            onSave.run();
        });
    }
}
