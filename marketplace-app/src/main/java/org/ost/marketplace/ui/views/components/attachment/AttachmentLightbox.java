package org.ost.marketplace.ui.views.components.attachment;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.ShortcutRegistration;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.IFrame;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.core.Configurable;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.utils.LightboxUtil;
import org.ost.platform.attachment.dto.AttachmentItemDto;
import org.ost.platform.attachment.model.AttachmentMediaContentType;
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

    private final transient I18nService i18nService;

    private transient ShortcutRegistration escShortcut;
    private Runnable                       closeAction;

    @Override
    public AttachmentLightbox configure(Parameters p) {
        addClassName("attachment-lightbox");
        AttachmentItemDto attachment = p.getAttachment();

        UiIconButton closeBtn = new UiIconButton(i18nService.get(ATTACHMENT_LIGHTBOX_CLOSE_TOOLTIP), VaadinIcon.CLOSE.create());
        closeBtn.addClassName("card-lightbox__close");
        closeBtn.getElement().addEventListener(CLICK_EVENT, _ -> {}).addEventData(STOP_PROPAGATION);

        String ct = attachment.contentType();
        if (AttachmentMediaContentType.isEmbedded(ct)) {
            IFrame iframe = buildIFrame(attachment);
            closeAction = () -> { iframe.setSrc("about:blank"); removeFromParent(); };
            addClickListener(_ -> close());
            add(closeBtn, iframe);
        } else if (AttachmentMediaContentType.isUploadedVideo(ct)) {
            Element videoEl = buildVideoElement(attachment);
            Div videoWrapper = new Div();
            videoWrapper.getElement().appendChild(videoEl);
            videoWrapper.getElement().addEventListener(CLICK_EVENT, _ -> {}).addEventData(STOP_PROPAGATION);
            closeAction = () -> { videoEl.executeJs("this.pause(); this.src='';"); removeFromParent(); };
            addClickListener(_ -> close());
            add(closeBtn, videoWrapper);
        } else {
            Image img = new Image(attachment.url(), attachment.filename());
            img.addClassName("attachment-lightbox__image");
            img.getElement().addEventListener(CLICK_EVENT, _ -> {}).addEventData(STOP_PROPAGATION);
            closeAction = this::removeFromParent;
            addClickListener(_ -> close());
            add(closeBtn, img);
        }
        closeBtn.addClickListener(_ -> close());

        addAttachListener(_ -> getUI().ifPresent(ui -> {
            escShortcut = Shortcuts.addShortcutListener(ui, this::close, Key.ESCAPE);
            getElement().executeJs(
                    "window.__lightboxTrigger = document.activeElement; $0.focus();", closeBtn.getElement());
        }));
        return this;
    }

    private void close() {
        if (escShortcut != null) {
            escShortcut.remove();
            escShortcut = null;
        }
        getElement().executeJs(
                "if (window.__lightboxTrigger) { window.__lightboxTrigger.focus(); window.__lightboxTrigger = null; }");
        closeAction.run();
    }

    private static IFrame buildIFrame(AttachmentItemDto attachment) {
        IFrame iframe = new IFrame(LightboxUtil.resolveEmbedUrl(attachment));
        iframe.addClassName("attachment-lightbox__iframe");
        LightboxUtil.applyEmbedIframeAttributes(iframe, LightboxUtil.isYoutube(attachment));
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
}
