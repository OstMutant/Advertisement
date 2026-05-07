package org.ost.advertisement.ui.views.components.overlay;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.common.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.dialogs.ConfirmActionDialog;
import org.ost.advertisement.ui.views.components.overlay.fields.OverlayBreadcrumbBackButton;
import org.ost.advertisement.ui.views.services.NotificationService;
import org.springframework.beans.factory.ObjectProvider;

import static org.ost.advertisement.common.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
@Getter
public class EntityOverlaySupport {

    private final I18nService                                 i18n;
    private final NotificationService                         notification;
    private final ConfirmActionDialog.Builder                 confirmDiscardBuilder;
    private final ObjectProvider<OverlayLayout>               layoutProvider;
    private final ObjectProvider<OverlayBreadcrumbBackButton> breadcrumbButtonProvider;

    public OverlayBreadcrumbBackButton createBreadcrumbButton(I18nKey labelKey, Runnable onBack) {
        OverlayBreadcrumbBackButton btn = breadcrumbButtonProvider.getObject()
                .configure(OverlayBreadcrumbBackButton.Parameters.builder()
                        .labelKey(labelKey)
                        .build());
        btn.addClickListener(_ -> onBack.run());
        return btn;
    }

    public OverlayLayout createLayout(OverlayBreadcrumbBackButton breadcrumbButton) {
        OverlayLayout layout = layoutProvider.getObject();
        layout.setBreadcrumbButton(breadcrumbButton);
        return layout;
    }

    public void handleCancel(boolean hasUnsavedChanges, Runnable doCancel) {
        if (hasUnsavedChanges) {
            confirmDiscardBuilder.build(
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
