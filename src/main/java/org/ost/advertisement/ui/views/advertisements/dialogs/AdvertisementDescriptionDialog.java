package org.ost.advertisement.ui.views.advertisements.dialogs;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.advertisements.dialogs.fields.DialogAdvertisementMetaPanel;
import org.ost.advertisement.ui.views.components.dialogs.BaseDialog;
import org.ost.advertisement.ui.views.components.dialogs.DialogLayout;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class AdvertisementDescriptionDialog extends BaseDialog {

    @Getter
    private final transient I18nService i18n;
    @Getter
    private final transient DialogLayout layout;
    private final transient DialogAdvertisementMetaPanel.Builder metaPanelBuilder;

    @Override
    @PostConstruct
    protected void init() {
        setCloseOnEsc(true);
        setCloseOnOutsideClick(true);
        addThemeName("advertisement-description");
    }

    private AdvertisementDescriptionDialog configure(AdvertisementInfoDto ad) {
        setHeaderTitle(ad.getTitle());

        Span content = new Span(ad.getDescription());
        content.addClassName("advertisement-description-content");

        VerticalLayout body = new VerticalLayout(content);
        body.addClassName("advertisement-description-body");
        body.setPadding(false);
        body.setSpacing(false);

        DialogAdvertisementMetaPanel meta = metaPanelBuilder.build(
                DialogAdvertisementMetaPanel.Parameters.builder()
                        .authorName(ad.getCreatedByUserName() != null ? ad.getCreatedByUserName() : "—")
                        .createdAt(ad.getCreatedAt())
                        .updatedAt(ad.getUpdatedAt())
                        .build());
        meta.addClassName("advertisement-description-meta");

        VerticalLayout container = new VerticalLayout(body, meta);
        container.addClassName("advertisement-description-layout");
        add(container);

        Button closeButton = new Button(i18n.get(ADVERTISEMENT_DESCRIPTION_DIALOG_CLOSE), _ -> close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        closeButton.addClassName("advertisement-description-close");
        getFooter().add(closeButton);

        return this;
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder {
        private final ObjectProvider<AdvertisementDescriptionDialog> provider;

        public AdvertisementDescriptionDialog build(AdvertisementInfoDto ad) {
            return provider.getObject().configure(ad);
        }
    }
}