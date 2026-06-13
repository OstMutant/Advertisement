package org.ost.ui.attachment;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import org.ost.attachment.entities.Attachment;
import org.ost.attachment.util.MediaContentTypeUtil;
import org.ost.attachment.util.YoutubeUtil;
import org.ost.platform.attachment.model.AttachmentMediaContentType;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

class CardLightboxStrip extends Div {

    CardLightboxStrip(List<Attachment> attachments) {
        addClassName("card-lightbox__strip");
        for (Attachment a : attachments) {
            Image thumb = new Image(thumbSrc(a), a.getFilename());
            thumb.addClassName("card-lightbox__thumb");
            add(thumb);
        }
    }

    void onSelect(IntConsumer handler) {
        var children = getChildren().toList();
        IntStream.range(0, children.size()).forEach(i ->
                children.get(i).getElement().addEventListener("click", _ -> handler.accept(i)));
    }

    void setActive(int idx) {
        getChildren().forEach(c -> c.getElement().getClassList().remove("card-lightbox__thumb--active"));
        getComponentAt(idx).getElement().getClassList().add("card-lightbox__thumb--active");
    }

    private static String thumbSrc(Attachment a) {
        if (AttachmentMediaContentType.YOUTUBE.getValue().equals(a.getContentType()))
            return YoutubeUtil.thumbnailUrl(YoutubeUtil.extractId(a.getUrl()));
        if (MediaContentTypeUtil.isVideo(a.getContentType()))
            return MediaContentTypeUtil.VIDEO_THUMBNAIL;
        return a.getUrl();
    }
}
