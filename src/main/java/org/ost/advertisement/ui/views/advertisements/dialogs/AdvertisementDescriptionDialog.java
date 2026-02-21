package org.ost.advertisement.ui.views.advertisements.dialogs;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
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
        super.init();
        // Description dialog is read-only — allow closing by clicking outside
        setCloseOnOutsideClick(true);
        addThemeName("advertisement-description");
    }

    private AdvertisementDescriptionDialog configure(AdvertisementInfoDto ad) {
        setHeaderTitle(ad.getTitle());
        addContent(ad);
        addMeta(ad);
        addActions();
        return this;
    }

    private void addContent(AdvertisementInfoDto ad) {
        // Plain Div — no Vaadin padding/margin/spacing defaults
        Div body = new Div();
        body.addClassName("advertisement-description-body");
        body.setText(ad.getDescription());

        layout.addScrollContent(body);
    }

    private void addMeta(AdvertisementInfoDto ad) {
        DialogAdvertisementMetaPanel meta = metaPanelBuilder.build(
                DialogAdvertisementMetaPanel.Parameters.builder()
                        .authorName(ad.getCreatedByUserName() != null ? ad.getCreatedByUserName() : "—")
                        .createdAt(ad.getCreatedAt())
                        .updatedAt(ad.getUpdatedAt())
                        .build());
        meta.addClassName("advertisement-description-meta");

        // Pinned above the footer, does not scroll with content
        layout.addBottomContent(meta);
    }

    private void addActions() {
        Button closeButton = new Button(i18n.get(ADVERTISEMENT_DESCRIPTION_DIALOG_CLOSE), _ -> close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        closeButton.addClassName("advertisement-description-close");
        getFooter().add(closeButton);
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