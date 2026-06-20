package org.ost.marketplace.ui.views.components.overlay;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.common.I18nKey;
import org.ost.marketplace.i18n.I18nService;
import org.ost.marketplace.ui.views.components.dialogs.ConfirmActionDialog;
import org.ost.marketplace.ui.views.components.overlay.fields.OverlayBreadcrumbBackButton;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.marketplace.ui.core.UiComponentFactory;

import static org.ost.marketplace.common.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
@Getter
public class EntityOverlaySupport {

    private final I18nService                                          i18n;
    private final NotificationService                                  notification;
    private final UiComponentFactory<OverlayBreadcrumbBackButton>        breadcrumbBackButtonFactory;
    private final UiComponentFactory<OverlayLayout>                      overlayLayoutFactory;
    private final UiComponentFactory<ConfirmActionDialog>                confirmDialogFactory;

    public OverlayBreadcrumbBackButton createBreadcrumbButton(I18nKey labelKey, Runnable onBack) {
        OverlayBreadcrumbBackButton btn = breadcrumbBackButtonFactory.build(
                OverlayBreadcrumbBackButton.Parameters.builder().labelKey(labelKey).build());
        btn.addClickListener(_ -> onBack.run());
        return btn;
    }

    public OverlayLayout createLayout(OverlayBreadcrumbBackButton breadcrumbButton) {
        OverlayLayout layout = overlayLayoutFactory.get();
        layout.setBreadcrumbButton(breadcrumbButton);
        return layout;
    }

    public void handleCancel(boolean hasUnsavedChanges, Runnable doCancel) {
        if (hasUnsavedChanges) {
            confirmDialogFactory.build(
                    ConfirmActionDialog.Parameters.builder()
                            .titleKey(OVERLAY_UNSAVED_TITLE)
                            .message(i18n.get(OVERLAY_UNSAVED_TEXT))
                            .confirmKey(OVERLAY_UNSAVED_CONFIRM)
                            .cancelKey(OVERLAY_UNSAVED_CANCEL)
                            .onConfirm(doCancel)
                            .build()
            ).open();
        } else {
            doCancel.run();
        }
    }
}
