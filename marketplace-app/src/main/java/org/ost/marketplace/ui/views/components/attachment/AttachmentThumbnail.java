package org.ost.marketplace.ui.views.components.attachment;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.platform.attachment.dto.AttachmentItemDto;
import org.ost.platform.attachment.dto.TempAttachmentDto;
import org.ost.platform.attachment.model.AttachmentMediaContentType;
import org.ost.platform.attachment.util.YoutubeUtil;
import org.springframework.context.annotation.Scope;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class AttachmentThumbnail extends Div {

    private final transient I18nService i18nService;

    @PostConstruct
    void init() {
        addClassName("attachment-gallery__item");
    }

    AttachmentThumbnail configureForView(AttachmentItemDto a, Runnable onClick) {
        Image img = buildImage(a.contentType(), a.url(), a.filename());
        img.addClickListener(_ -> onClick.run());
        add(img);
        if (AttachmentMediaContentType.isVideo(a.contentType())) {
            Icon play = playIcon();
            play.addClickListener(_ -> onClick.run());
            add(play);
        }
        return this;
    }

    AttachmentThumbnail configureForEdit(AttachmentItemDto a, Runnable onDelete) {
        add(buildImage(a.contentType(), a.url(), a.filename()));
        if (AttachmentMediaContentType.isVideo(a.contentType())) add(playIcon());
        add(deleteButton(onDelete));
        return this;
    }

    AttachmentThumbnail configureForTemp(TempAttachmentDto temp, Runnable onDelete) {
        add(buildImage(temp.contentType(), temp.tempUrl(), temp.filename()));
        if (AttachmentMediaContentType.isVideo(temp.contentType())) add(playIcon());
        add(deleteButton(onDelete));
        return this;
    }

    private static Image buildImage(String contentType, String url, String alt) {
        Image img = new Image(thumbSrc(contentType, url), alt);
        img.addClassName("attachment-gallery__image");
        return img;
    }

    private Button deleteButton(Runnable onDelete) {
        UiIconButton btn = new UiIconButton(i18nService.get(ATTACHMENT_GALLERY_REMOVE_TOOLTIP), VaadinIcon.CLOSE_SMALL.create());
        btn.addThemeVariants(ButtonVariant.LUMO_ERROR);
        btn.addClassName("attachment-gallery__delete-btn");
        btn.addClickListener(_ -> {
            this.removeFromParent();
            onDelete.run();
        });
        return btn;
    }

    private static Icon playIcon() {
        Icon i = VaadinIcon.PLAY_CIRCLE_O.create();
        i.addClassName("attachment-gallery__play-icon");
        return i;
    }

    private static String thumbSrc(String contentType, String url) {
        if (AttachmentMediaContentType.YOUTUBE.getValue().equals(contentType))
            return YoutubeUtil.thumbnailUrl(YoutubeUtil.extractId(url));
        if (AttachmentMediaContentType.isVideo(contentType))
            return AttachmentMediaContentType.VIDEO_THUMBNAIL;
        return url;
    }
}
