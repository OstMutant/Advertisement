package org.ost.ui.attachment;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.IFrame;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.dom.Element;
import org.ost.attachment.entities.Attachment;
import org.ost.attachment.util.MediaContentTypeUtil;
import org.ost.attachment.util.YoutubeUtil;
import org.ost.platform.attachment.model.AttachmentMediaContentType;

class AttachmentLightbox extends Div {

    private static final String CLICK_EVENT      = "click";
    private static final String STOP_PROPAGATION = "event.stopPropagation()";

    private AttachmentLightbox(Attachment attachment) {
        addClassName("attachment-lightbox");

        Button closeBtn = new Button(VaadinIcon.CLOSE.create(), _ -> close(null));
        closeBtn.addClassName("card-lightbox__close");
        closeBtn.getElement().addEventListener(CLICK_EVENT, _ -> {}).addEventData(STOP_PROPAGATION);

        String ct = attachment.getContentType();
        if (MediaContentTypeUtil.isEmbedded(ct)) {
            IFrame iframe = buildIFrame(attachment);
            addClickListener(_ -> close(iframe));
            add(closeBtn, iframe);
        } else if (MediaContentTypeUtil.isUploadedVideo(ct)) {
            Element videoEl = buildVideoElement(attachment);
            Div videoWrapper = new Div();
            videoWrapper.getElement().appendChild(videoEl);
            videoWrapper.getElement().addEventListener(CLICK_EVENT, _ -> {}).addEventData(STOP_PROPAGATION);
            addClickListener(_ -> {
                videoEl.executeJs("this.pause(); this.src='';");
                removeFromParent();
            });
            add(closeBtn, videoWrapper);
        } else {
            Image img = new Image(attachment.getUrl(), attachment.getFilename());
            img.addClassName("attachment-lightbox__image");
            img.getElement().addEventListener(CLICK_EVENT, _ -> {}).addEventData(STOP_PROPAGATION);
            addClickListener(_ -> removeFromParent());
            add(closeBtn, img);
        }
    }

    static void open(Attachment attachment, UI ui) {
        ui.getElement().appendChild(new AttachmentLightbox(attachment).getElement());
    }

    private void close(IFrame iframe) {
        if (iframe != null) iframe.setSrc("about:blank");
        removeFromParent();
    }

    private static IFrame buildIFrame(Attachment attachment) {
        IFrame iframe = new IFrame(resolveEmbedUrl(attachment));
        iframe.addClassName("attachment-lightbox__iframe");
        iframe.getElement().setAttribute("allow",
                "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture");
        iframe.getElement().setAttribute("allowfullscreen", "true");
        iframe.getElement().setAttribute("sandbox", "allow-scripts allow-same-origin allow-presentation");
        iframe.getElement().addEventListener(CLICK_EVENT, _ -> {}).addEventData(STOP_PROPAGATION);
        return iframe;
    }

    private static Element buildVideoElement(Attachment attachment) {
        Element videoEl = new Element("video");
        videoEl.setAttribute("controls", "");
        videoEl.setAttribute("src", attachment.getUrl());
        videoEl.getClassList().add("attachment-lightbox__video");
        return videoEl;
    }

    private static String resolveEmbedUrl(Attachment attachment) {
        if (AttachmentMediaContentType.YOUTUBE.getValue().equals(attachment.getContentType()))
            return YoutubeUtil.embedUrl(YoutubeUtil.extractId(attachment.getUrl()));
        return attachment.getUrl();
    }
}
