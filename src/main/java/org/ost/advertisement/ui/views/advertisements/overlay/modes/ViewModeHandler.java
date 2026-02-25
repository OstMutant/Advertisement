package org.ost.advertisement.ui.views.advertisements.overlay.modes;

import com.vaadin.flow.component.html.Div;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class ViewModeHandler implements ModeHandler {

    private final AccessEvaluator                                 access;
    private final OverlayAdvertisementMetaPanel.Builder           metaPanelBuilder;
    private final ObjectProvider<OverlayAdvertisementEditButton>  editButtonProvider;
    private final ObjectProvider<OverlayAdvertisementCloseButton> closeButtonProvider;

    private Runnable onEdit;
    private Runnable onClose;

    @Override
    public void setCallbacks(Runnable primary, Runnable secondary) {
        this.onEdit  = primary;
        this.onClose = secondary;
    }

    @Override
    public void activate(OverlaySession s, OverlayLayout layout) {
        AdvertisementInfoDto ad = s.ad();

        H2 title = new H2(ad.getTitle());
        title.addClassName("overlay__view-title");

        Span description = new Span(ad.getDescription());
        description.addClassName("overlay__view-description");

        Div metaContainer = new Div();
        metaContainer.addClassName("overlay__meta-container");
        OverlayMetaHelper.rebuild(metaContainer, metaPanelBuilder, ad);

        OverlayAdvertisementEditButton  editButton  = editButtonProvider.getObject();
        OverlayAdvertisementCloseButton closeButton = closeButtonProvider.getObject();
        editButton.addClickListener(_  -> onEdit.run());
        closeButton.addClickListener(_ -> onClose.run());
        editButton.setVisible(access.canOperate(ad));

        layout.setContent(new Div(title, description, metaContainer));
        layout.setHeaderActions(new Div(editButton, closeButton));
    }

    @Override
    public void deactivate() {}
}