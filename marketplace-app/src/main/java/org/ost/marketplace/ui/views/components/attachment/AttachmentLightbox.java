package org.ost.marketplace.ui.views.components.attachment;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.IFrame;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.marketplace.ui.core.Configurable;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.platform.attachment.dto.AttachmentItemDto;
import org.ost.platform.attachment.model.AttachmentMediaContentType;
import org.ost.platform.attachment.util.YoutubeUtil;
import org.springframework.context.annotation.Scope;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class AttachmentLightbox extends Div implements Configurable<AttachmentLightbox, AttachmentLightbox.Parameters> {

    private static final String CLICK_EVENT      = "click";
    private static final String STOP_PROPAGATION = "event.stopPropagation()";

    @Value
    public static class Parameters {
        AttachmentItemDto attachment;
    }

    private final transient UiComponentFactory<UiIconButton> iconButtonFactory;

    @Override
    public AttachmentLightbox configure(Parameters p) {
        addClassName("attachment-lightbox");
        AttachmentItemDto attachment = p.getAttachment();

        UiIconButton closeBtn = iconButtonFactory.build(
                UiIconButton.Parameters.builder().labelKey(ATTACHMENT_LIGHTBOX_CLOSE_TOOLTIP).icon(VaadinIcon.CLOSE.create()).build());
        closeBtn.addClickListener(_ -> close(null));
        closeBtn.addClassName("card-lightbox__close");
        closeBtn.getElement().addEventListener(CLICK_EVENT, _ -> {}).addEventData(STOP_PROPAGATION);

        String ct = attachment.contentType();
        if (AttachmentMediaContentType.isEmbedded(ct)) {
            IFrame iframe = buildIFrame(attachment);
            addClickListener(_ -> close(iframe));
            add(closeBtn, iframe);
        } else if (AttachmentMediaContentType.isUploadedVideo(ct)) {
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
            Image img = new Image(attachment.url(), attachment.filename());
            img.addClassName("attachment-lightbox__image");
            img.getElement().addEventListener(CLICK_EVENT, _ -> {}).addEventData(STOP_PROPAGATION);
            addClickListener(_ -> removeFromParent());
            add(closeBtn, img);
        }
        return this;
    }

    private void close(IFrame iframe) {
        if (iframe != null) iframe.setSrc("about:blank");
        removeFromParent();
    }

    private static IFrame buildIFrame(AttachmentItemDto attachment) {
        IFrame iframe = new IFrame(resolveEmbedUrl(attachment));
        iframe.addClassName("attachment-lightbox__iframe");
        iframe.getElement().setAttribute("allow",
                "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture");
        iframe.getElement().setAttribute("allowfullscreen", "true");
        iframe.getElement().setAttribute("sandbox", "allow-scripts allow-same-origin allow-presentation");
        iframe.getElement().addEventListener(CLICK_EVENT, _ -> {}).addEventData(STOP_PROPAGATION);
        return iframe;
    }

    private static Element buildVideoElement(AttachmentItemDto attachment) {
        Element videoEl = new Element("video");
        videoEl.setAttribute("controls", "");
        videoEl.setAttribute("src", attachment.url());
        videoEl.getClassList().add("attachment-lightbox__video");
        return videoEl;
    }

    private static String resolveEmbedUrl(AttachmentItemDto attachment) {
        if (AttachmentMediaContentType.YOUTUBE.getValue().equals(attachment.contentType()))
            return YoutubeUtil.embedUrl(YoutubeUtil.extractId(attachment.url()));
        return attachment.url();
    }
}
