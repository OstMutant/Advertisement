package org.ost.attachment.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import org.ost.attachment.entities.Attachment;
import org.ost.attachment.services.AttachmentService.TempAttachment;
import org.ost.attachment.util.MediaContentTypeUtil;
import org.ost.attachment.util.YoutubeUtil;
import org.ost.platform.attachment.model.AttachmentMediaContentType;

class AttachmentThumbnail extends Div {

    private AttachmentThumbnail() {
        addClassName("attachment-gallery__item");
    }

    static AttachmentThumbnail forView(Attachment a, Runnable onClick) {
        AttachmentThumbnail t = new AttachmentThumbnail();
        Image img = buildImage(a.getContentType(), a.getUrl(), a.getFilename());
        img.addClickListener(_ -> onClick.run());
        t.add(img);
        if (MediaContentTypeUtil.isVideo(a.getContentType())) {
            Icon play = playIcon();
            play.addClickListener(_ -> onClick.run());
            t.add(play);
        }
        return t;
    }

    static AttachmentThumbnail forEdit(Attachment a, Runnable onDelete) {
        AttachmentThumbnail t = new AttachmentThumbnail();
        t.add(buildImage(a.getContentType(), a.getUrl(), a.getFilename()));
        if (MediaContentTypeUtil.isVideo(a.getContentType())) t.add(playIcon());
        t.add(deleteButton(t, onDelete));
        return t;
    }

    static AttachmentThumbnail forTemp(TempAttachment temp, Runnable onDelete) {
        AttachmentThumbnail t = new AttachmentThumbnail();
        t.add(buildImage(temp.contentType(), temp.tempUrl(), temp.filename()));
        if (MediaContentTypeUtil.isVideo(temp.contentType())) t.add(playIcon());
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
        if (MediaContentTypeUtil.isEmbedded(contentType) || MediaContentTypeUtil.isUploadedVideo(contentType))
            return MediaContentTypeUtil.VIDEO_THUMBNAIL;
        return url;
    }
}
