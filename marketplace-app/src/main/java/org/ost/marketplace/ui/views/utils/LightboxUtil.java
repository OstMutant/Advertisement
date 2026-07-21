package org.ost.marketplace.ui.views.utils;

import com.vaadin.flow.component.html.IFrame;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.platform.attachment.dto.AttachmentItemDto;
import org.ost.platform.attachment.model.AttachmentMediaContentType;
import org.ost.platform.attachment.util.YoutubeUtil;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LightboxUtil {

    public static String resolveEmbedUrl(AttachmentItemDto attachment) {
        if (AttachmentMediaContentType.YOUTUBE.getValue().equals(attachment.contentType()))
            return YoutubeUtil.embedUrl(YoutubeUtil.extractId(attachment.url()));
        return attachment.url();
    }

    public static void applyEmbedIframeAttributes(IFrame iframe) {
        iframe.getElement().setAttribute("allow",
                "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture");
        iframe.getElement().setAttribute("allowfullscreen", "true");
        iframe.getElement().setAttribute("sandbox", "allow-scripts allow-presentation"); // no allow-same-origin -- sandbox escape
    }
}
