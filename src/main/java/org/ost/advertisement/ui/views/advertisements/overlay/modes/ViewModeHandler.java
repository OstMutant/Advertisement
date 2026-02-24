package org.ost.advertisement.ui.views.advertisements.overlay.modes;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.security.AccessEvaluator;
import org.ost.advertisement.ui.views.advertisements.overlay.OverlayMetaHelper;
import org.ost.advertisement.ui.views.advertisements.overlay.OverlaySession;
import org.ost.advertisement.ui.views.advertisements.overlay.fields.OverlayAdvertisementCloseButton;
import org.ost.advertisement.ui.views.advertisements.overlay.fields.OverlayAdvertisementEditButton;
import org.ost.advertisement.ui.views.advertisements.overlay.fields.OverlayAdvertisementMetaPanel;
import org.ost.advertisement.ui.views.components.overlay.OverlayLayout;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class ViewModeHandler implements ModeHandler {

    private final AccessEvaluator                       access;
    private final OverlayAdvertisementMetaPanel.Builder metaPanelBuilder;
    private final OverlayAdvertisementEditButton        editButton;
    private final OverlayAdvertisementCloseButton       closeButton;

    private final H2   title       = new H2();
    private final Span description = new Span();

    private OverlayLayout layout;
    private Runnable      onEdit;
    private Runnable      onClose;

    // primary = onEdit, secondary = onClose
    @Override
    public void configure(OverlayLayout layout, Runnable primary, Runnable secondary) {
        this.layout  = layout;
        this.onEdit  = primary;
        this.onClose = secondary;
    }

    @Override
    public void init() {
        title.addClassName("overlay__view-title");
        description.addClassName("overlay__view-description");
        layout.addViewContent(title, description);

        layout.addHeaderActions(editButton, closeButton);
        editButton.addClickListener(_  -> onEdit.run());
        closeButton.addClickListener(_ -> onClose.run());

        deactivate();
    }

    @Override
    public void activate(OverlaySession s) {
        AdvertisementInfoDto ad = s.ad();
        title.setText(ad.getTitle());
        description.setText(ad.getDescription());
        OverlayMetaHelper.rebuild(layout, metaPanelBuilder, ad);

        layout.getViewBody().setVisible(true);
        layout.getMetaContainer().setVisible(true);
        editButton.setVisible(access.canOperate(ad));
        closeButton.setVisible(true);
    }

    @Override
    public void deactivate() {
        layout.getViewBody().setVisible(false);
        layout.getMetaContainer().setVisible(false);
        editButton.setVisible(false);
        closeButton.setVisible(false);
    }
}