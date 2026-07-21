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

    public static boolean isYoutube(AttachmentItemDto attachment) {
        return AttachmentMediaContentType.YOUTUBE.getValue().equals(attachment.contentType());
    }

    // YouTube's player needs Cache Storage (allow-same-origin) to bootstrap; safe only for YouTube since we build that URL ourselves.
    public static String embedSandbox(boolean allowSameOrigin) {
        return allowSameOrigin ? "allow-scripts allow-same-origin allow-presentation" : "allow-scripts allow-presentation";
    }

    public static void applyEmbedIframeAttributes(IFrame iframe, boolean allowSameOrigin) {
        iframe.getElement().setAttribute("allow",
                "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture");
        iframe.getElement().setAttribute("allowfullscreen", "true");
        iframe.getElement().setAttribute("sandbox", embedSandbox(allowSameOrigin));
    }
}
