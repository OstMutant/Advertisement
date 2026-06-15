package org.ost.marketplace.ui.attachment;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import org.ost.platform.attachment.dto.AttachmentItemDto;
import org.ost.platform.attachment.model.AttachmentMediaContentType;
import org.ost.platform.attachment.util.YoutubeUtil;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

class CardLightboxStrip extends Div {

    CardLightboxStrip(List<AttachmentItemDto> attachments) {
        addClassName("card-lightbox__strip");
        for (AttachmentItemDto a : attachments) {
            Image thumb = new Image(thumbSrc(a), a.filename());
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

    private static String thumbSrc(AttachmentItemDto a) {
        if (AttachmentMediaContentType.YOUTUBE.getValue().equals(a.contentType()))
            return YoutubeUtil.thumbnailUrl(YoutubeUtil.extractId(a.url()));
        if (AttachmentMediaContentType.isVideo(a.contentType()))
            return AttachmentMediaContentType.VIDEO_THUMBNAIL;
        return a.url();
    }
}
