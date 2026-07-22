package org.ost.marketplace.ui.views.components.attachment;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.IFrame;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.utils.LightboxUtil;
import org.ost.platform.attachment.dto.AttachmentItemDto;
import org.ost.platform.attachment.model.AttachmentMediaContentType;
import org.springframework.context.annotation.Scope;

import java.util.UUID;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class CardLightboxViewer extends HorizontalLayout {

    private final Image   mainImg  = new Image();
    private final IFrame  iframe   = new IFrame();
    private final Element videoEl  = new Element("video");
    private final Div     mainVideo = new Div();
    private UiIconButton  prevBtn;
    private UiIconButton  nextBtn;

    // Unique per instance -- see update()'s comment for why these replace a shared CSS class in
    // the page-level executeJs() calls below.
    private final String iframeId  = "card-lightbox-iframe-" + UUID.randomUUID();
    private final String videoElId = "card-lightbox-video-" + UUID.randomUUID();

    private final transient UiComponentFactory<UiIconButton> iconButtonFactory;

    @PostConstruct
    void init() {
        mainImg.addClassName("card-lightbox__main-image");

        iframe.addClassName("card-lightbox__iframe");
        iframe.getElement().setAttribute("id", iframeId);
        LightboxUtil.applyEmbedIframeAttributes(iframe, false);
        iframe.setVisible(false);

        videoEl.setAttribute("id", videoElId);
        videoEl.setAttribute("controls", "");
        videoEl.getClassList().add("card-lightbox__main-video");
        mainVideo.addClassName("card-lightbox__main-video-wrapper");
        mainVideo.getElement().appendChild(videoEl);
        mainVideo.setVisible(false);

        prevBtn = iconButtonFactory.build(
                UiIconButton.Parameters.builder().labelKey(ATTACHMENT_LIGHTBOX_PREV_TOOLTIP).icon(VaadinIcon.ANGLE_LEFT.create()).build());
        nextBtn = iconButtonFactory.build(
                UiIconButton.Parameters.builder().labelKey(ATTACHMENT_LIGHTBOX_NEXT_TOOLTIP).icon(VaadinIcon.ANGLE_RIGHT.create()).build());
        prevBtn.addClassName("card-lightbox__nav");
        nextBtn.addClassName("card-lightbox__nav");

        addClassName("card-lightbox__viewer");
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        add(prevBtn, mainImg, iframe, mainVideo, nextBtn);
    }

    void onPrev(Runnable handler) { prevBtn.addClickListener(_ -> handler.run()); }
    void onNext(Runnable handler) { nextBtn.addClickListener(_ -> handler.run()); }

    void setNavVisible(boolean visible) {
        prevBtn.setVisible(visible);
        nextBtn.setVisible(visible);
    }

    private static final String BLANK_SRC = "about:blank";

    // Vaadin only reliably updates a rendered iframe/video src via page-level executeJs()
    // targeted by the element's own unique id -- element-scoped calls and attribute diffing don't
    // apply reliably once the element has already rendered once (see git history 4310b0be).
    void update(AttachmentItemDto a) {
        String ct = a.contentType();
        if (AttachmentMediaContentType.isEmbedded(ct)) {
            videoEl.setAttribute("src", "");
            setVideoSrcViaPage("", true);
            String embedUrl = LightboxUtil.resolveEmbedUrl(a);
            String sandbox  = LightboxUtil.embedSandbox(LightboxUtil.isYoutube(a));
            iframe.getElement().setAttribute("sandbox", sandbox);
            iframe.getElement().setAttribute("src", embedUrl);
            setIframeSrcViaPage(sandbox, embedUrl);
            mainImg.setVisible(false);
            mainVideo.setVisible(false);
            iframe.setVisible(true);
        } else if (AttachmentMediaContentType.isUploadedVideo(ct)) {
            iframe.getElement().setAttribute("src", BLANK_SRC);
            setIframeSrcViaPage(BLANK_SRC);
            videoEl.setAttribute("src", a.url());
            setVideoSrcViaPage(a.url(), false);
            mainImg.setVisible(false);
            iframe.setVisible(false);
            mainVideo.setVisible(true);
        } else {
            iframe.getElement().setAttribute("src", BLANK_SRC);
            setIframeSrcViaPage(BLANK_SRC);
            videoEl.setAttribute("src", "");
            setVideoSrcViaPage("", true);
            mainVideo.setVisible(false);
            iframe.setVisible(false);
            mainImg.setSrc(a.url());
            mainImg.setAlt(a.filename());
            mainImg.setVisible(true);
        }
    }

    private void setIframeSrcViaPage(String src) {
        getUI().ifPresent(ui -> ui.getPage().executeJs(
                "var f = document.getElementById($0); if (f) f.src = $1;", iframeId, src));
    }

    // sandbox must be set before src -- it only applies on the iframe's next navigation
    private void setIframeSrcViaPage(String sandbox, String src) {
        getUI().ifPresent(ui -> ui.getPage().executeJs(
                "var f = document.getElementById($0); if (f) { f.setAttribute('sandbox', $1); f.src = $2; }",
                iframeId, sandbox, src));
    }

    private void setVideoSrcViaPage(String src, boolean pause) {
        getUI().ifPresent(ui -> ui.getPage().executeJs(
                "var v = document.getElementById($0); if (v) { v.src = $1; if ($2) v.pause(); else v.load(); }",
                videoElId, src, pause));
    }

}
