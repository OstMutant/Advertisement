package org.ost.marketplace.ui.views.components.attachment;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.platform.attachment.dto.AttachmentItemDto;
import org.springframework.context.annotation.Scope;

import java.util.List;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class CardMediaLightbox {

    private final transient UiComponentFactory<UiIconButton> iconButtonFactory;
    private final transient UiComponentFactory<CardLightboxViewer> viewerFactory;

    public void open(@NonNull List<AttachmentItemDto> attachments, int startIndex) {
        int[] idx = {startIndex};

        CardLightboxViewer viewer = viewerFactory.get();
        CardLightboxStrip  strip  = new CardLightboxStrip(attachments);
        strip.onSelect(fi -> { idx[0] = fi; navigate(viewer, strip, attachments, fi); });

        viewer.onPrev(() -> { idx[0] = (idx[0] - 1 + attachments.size()) % attachments.size(); navigate(viewer, strip, attachments, idx[0]); });
        viewer.onNext(() -> { idx[0] = (idx[0] + 1) % attachments.size();                      navigate(viewer, strip, attachments, idx[0]); });
        viewer.setNavVisible(attachments.size() > 1);

        VerticalLayout content = new VerticalLayout(viewer, strip);
        content.addClassName("card-lightbox__content");
        content.setPadding(false);
        content.setAlignItems(FlexComponent.Alignment.CENTER);

        CardLightboxDialog dialog = new CardLightboxDialog();
        UiIconButton closeBtn = iconButtonFactory.build(
                UiIconButton.Parameters.builder().labelKey(ATTACHMENT_LIGHTBOX_CLOSE_TOOLTIP).icon(VaadinIcon.CLOSE.create()).build());
        closeBtn.addClickListener(_ -> dialog.close());
        closeBtn.addClassName("card-lightbox__close");
        dialog.add(closeBtn, content);

        navigate(viewer, strip, attachments, idx[0]);
        dialog.open();
    }

    private static void navigate(CardLightboxViewer viewer, CardLightboxStrip strip,
                                  List<AttachmentItemDto> attachments, int idx) {
        viewer.update(attachments.get(idx));
        strip.setActive(idx);
    }
}
