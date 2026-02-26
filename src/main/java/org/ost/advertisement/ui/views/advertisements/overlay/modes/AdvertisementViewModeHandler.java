package org.ost.advertisement.ui.views.advertisements.overlay.modes;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.security.AccessEvaluator;
import org.ost.advertisement.ui.views.advertisements.overlay.fields.OverlayAdvertisementMetaPanel;
import org.ost.advertisement.ui.views.components.fields.IconButton;
import org.ost.advertisement.ui.views.components.fields.PrimaryButton;
import org.ost.advertisement.ui.views.components.overlay.ModeHandler;
import org.ost.advertisement.ui.views.components.overlay.OverlayLayout;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class AdvertisementViewModeHandler implements ModeHandler {

    private final AccessEvaluator                       access;
    private final OverlayAdvertisementMetaPanel         metaPanel;
    private final ObjectProvider<PrimaryButton>         editButtonProvider;
    private final ObjectProvider<IconButton>            closeButtonProvider;

    private Parameters params;

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull AdvertisementInfoDto ad;
        @NonNull Runnable             onEdit;
        @NonNull Runnable             onClose;
    }

    private AdvertisementViewModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    @Override
    public void activate(OverlayLayout layout) {
        H2 title = new H2(params.getAd().getTitle());
        title.addClassName("overlay__view-title");

        Span description = new Span(params.getAd().getDescription());
        description.addClassName("overlay__view-description");

        PrimaryButton editButton = editButtonProvider.getObject().configure(
                PrimaryButton.Parameters.builder()
                        .labelKey(ADVERTISEMENT_CARD_BUTTON_EDIT)
                        .build());
        IconButton closeButton = closeButtonProvider.getObject().configure(
                IconButton.Parameters.builder()
                        .labelKey(MAIN_TAB_ADVERTISEMENTS)
                        .icon(VaadinIcon.CLOSE.create())
                        .build());

        editButton.addClickListener(_  -> params.getOnEdit().run());
        closeButton.addClickListener(_ -> params.getOnClose().run());
        editButton.setVisible(access.canOperate(params.getAd()));

        layout.setContent(new Div(title, description,
                metaPanel.configure(OverlayAdvertisementMetaPanel.Parameters.from(params.getAd()))));
        layout.setHeaderActions(new Div(editButton, closeButton));
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder {
        private final ObjectProvider<AdvertisementViewModeHandler> provider;

        public AdvertisementViewModeHandler build(Parameters p) {
            return provider.getObject().configure(p);
        }
    }
}
