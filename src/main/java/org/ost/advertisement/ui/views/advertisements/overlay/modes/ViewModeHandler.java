package org.ost.advertisement.ui.views.advertisements.overlay.modes;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.security.AccessEvaluator;
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

    private Parameters params;

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull AdvertisementInfoDto ad;
        @NonNull Runnable             onEdit;
        @NonNull Runnable             onClose;
    }

    private ViewModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    @Override
    public void activate(OverlayLayout layout) {
        H2 title = new H2(params.getAd().getTitle());
        title.addClassName("overlay__view-title");

        Span description = new Span(params.getAd().getDescription());
        description.addClassName("overlay__view-description");

        OverlayAdvertisementEditButton  editButton  = editButtonProvider.getObject();
        OverlayAdvertisementCloseButton closeButton = closeButtonProvider.getObject();
        editButton.addClickListener(_  -> params.getOnEdit().run());
        closeButton.addClickListener(_ -> params.getOnClose().run());
        editButton.setVisible(access.canOperate(params.getAd()));

        layout.setContent(new Div(title, description, metaPanelBuilder.build(params.getAd())));
        layout.setHeaderActions(new Div(editButton, closeButton));
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder {
        private final ObjectProvider<ViewModeHandler> provider;

        public ViewModeHandler build(Parameters p) {
            return provider.getObject().configure(p);
        }
    }
}