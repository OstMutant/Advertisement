package org.ost.marketplace.ui.views.components.overlay;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.views.components.dialogs.ConfirmActionDialog;
import org.ost.marketplace.ui.views.components.overlay.fields.OverlayBreadcrumbBackButton;
import org.ost.marketplace.ui.views.services.NotificationService;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
@Getter
public class EntityOverlaySupport {

    private final I18nService                                          i18n;
    private final NotificationService                                  notification;

    public OverlayBreadcrumbBackButton createBreadcrumbButton(I18nKey labelKey, Runnable onBack) {
        OverlayBreadcrumbBackButton btn = new OverlayBreadcrumbBackButton(i18n.get(labelKey));
        btn.addClickListener(_ -> onBack.run());
        return btn;
    }

    public OverlayLayout createLayout(OverlayBreadcrumbBackButton breadcrumbButton) {
        OverlayLayout layout = new OverlayLayout();
        layout.setBreadcrumbButton(breadcrumbButton);
        return layout;
    }

    public void handleCancel(boolean hasUnsavedChanges, Runnable doCancel) {
        if (hasUnsavedChanges) {
            new ConfirmActionDialog(
                    i18n.get(OVERLAY_UNSAVED_TITLE),
                    i18n.get(OVERLAY_UNSAVED_TEXT),
                    i18n.get(OVERLAY_UNSAVED_CONFIRM),
                    i18n.get(OVERLAY_UNSAVED_CANCEL),
                    doCancel
            ).open();
        } else {
            doCancel.run();
        }
    }
}
