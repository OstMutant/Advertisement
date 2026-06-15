package org.ost.ui.attachment;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import org.ost.platform.attachment.dto.AttachmentItemDto;
import org.ost.platform.attachment.dto.TempAttachmentDto;
import org.ost.platform.attachment.model.AttachmentMediaContentType;
import org.ost.platform.attachment.util.YoutubeUtil;

class AttachmentThumbnail extends Div {

    private AttachmentThumbnail() {
        addClassName("attachment-gallery__item");
    }

    static AttachmentThumbnail forView(AttachmentItemDto a, Runnable onClick) {
        AttachmentThumbnail t = new AttachmentThumbnail();
        Image img = buildImage(a.contentType(), a.url(), a.filename());
        img.addClickListener(_ -> onClick.run());
        t.add(img);
        if (AttachmentMediaContentType.isVideo(a.contentType())) {
            Icon play = playIcon();
            play.addClickListener(_ -> onClick.run());
            t.add(play);
        }
        return t;
    }

    static AttachmentThumbnail forEdit(AttachmentItemDto a, Runnable onDelete) {
        AttachmentThumbnail t = new AttachmentThumbnail();
        t.add(buildImage(a.contentType(), a.url(), a.filename()));
        if (AttachmentMediaContentType.isVideo(a.contentType())) t.add(playIcon());
        t.add(deleteButton(t, onDelete));
        return t;
    }

    static AttachmentThumbnail forTemp(TempAttachmentDto temp, Runnable onDelete) {
        AttachmentThumbnail t = new AttachmentThumbnail();
        t.add(buildImage(temp.contentType(), temp.tempUrl(), temp.filename()));
        if (AttachmentMediaContentType.isVideo(temp.contentType())) t.add(playIcon());
        t.add(deleteButton(t, onDelete));
        return t;
    }

    private static Image buildImage(String contentType, String url, String alt) {
        Image img = new Image(thumbSrc(contentType, url), alt);
        img.addClassName("attachment-gallery__image");
        return img;
    }

    private static Button deleteButton(Div self, Runnable onDelete) {
        Button btn = new Button(VaadinIcon.CLOSE_SMALL.create(), _ -> {
            self.removeFromParent();
            onDelete.run();
        });
        btn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_ICON);
        btn.addClassName("attachment-gallery__delete-btn");
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
