package org.ost.marketplace.ui.attachment;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.IFrame;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.dom.Element;
import org.ost.platform.attachment.dto.AttachmentItemDto;
import org.ost.platform.attachment.model.AttachmentMediaContentType;
import org.ost.platform.attachment.util.YoutubeUtil;

class CardLightboxViewer extends HorizontalLayout {

    private final Image   mainImg  = new Image();
    private final IFrame  iframe   = new IFrame();
    private final Element videoEl  = new Element("video");
    private final Div     mainVideo = new Div();
    private final Button  prevBtn;
    private final Button  nextBtn;

    CardLightboxViewer() {
        mainImg.addClassName("card-lightbox__main-image");

        iframe.addClassName("card-lightbox__iframe");
        iframe.getElement().setAttribute("allow",
                "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture");
        iframe.getElement().setAttribute("allowfullscreen", "true");
        iframe.getElement().setAttribute("sandbox", "allow-scripts allow-same-origin allow-presentation");
        iframe.setVisible(false);

        videoEl.setAttribute("controls", "");
        videoEl.getClassList().add("card-lightbox__main-video");
        mainVideo.addClassName("card-lightbox__main-video-wrapper");
        mainVideo.getElement().appendChild(videoEl);
        mainVideo.setVisible(false);

        prevBtn = new Button(VaadinIcon.ANGLE_LEFT.create());
        nextBtn = new Button(VaadinIcon.ANGLE_RIGHT.create());
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

    void update(AttachmentItemDto a) {
        String ct = a.contentType();
        if (AttachmentMediaContentType.isEmbedded(ct)) {
            getUI().ifPresent(ui -> ui.getPage().executeJs(
                "var v=document.querySelector('.card-lightbox__main-video'); if(v){v.pause();v.src='';}"));
            String embedUrl = embedSrc(a);
            iframe.getElement().setAttribute("src", embedUrl);
            getUI().ifPresent(ui -> ui.getPage().executeJs(
                "var f=document.querySelector('.card-lightbox__iframe'); if(f) f.src=$0;", embedUrl));
            mainImg.setVisible(false);
            mainVideo.setVisible(false);
            iframe.setVisible(true);
        } else if (AttachmentMediaContentType.isUploadedVideo(ct)) {
            iframe.getElement().setAttribute("src", "about:blank");
            getUI().ifPresent(ui -> ui.getPage().executeJs(
                "var f=document.querySelector('.card-lightbox__iframe'); if(f) f.src='about:blank';"));
            videoEl.setAttribute("src", a.url());
            mainImg.setVisible(false);
            iframe.setVisible(false);
            mainVideo.setVisible(true);
            getUI().ifPresent(ui -> ui.getPage().executeJs(
                "var v=document.querySelector('.card-lightbox__main-video'); if(v){v.src=$0; v.load();}", a.url()));
        } else {
            iframe.getElement().setAttribute("src", "about:blank");
            getUI().ifPresent(ui -> ui.getPage().executeJs(
                "var f=document.querySelector('.card-lightbox__iframe'); if(f) f.src='about:blank';" +
                "var v=document.querySelector('.card-lightbox__main-video'); if(v){v.pause();v.src='';}"));
            mainVideo.setVisible(false);
            iframe.setVisible(false);
            mainImg.setSrc(a.url());
            mainImg.setAlt(a.filename());
            mainImg.setVisible(true);
        }
    }

    private static String embedSrc(AttachmentItemDto a) {
        if (AttachmentMediaContentType.YOUTUBE.getValue().equals(a.contentType()))
            return YoutubeUtil.embedUrl(YoutubeUtil.extractId(a.url()));
        return a.url();
    }
}
