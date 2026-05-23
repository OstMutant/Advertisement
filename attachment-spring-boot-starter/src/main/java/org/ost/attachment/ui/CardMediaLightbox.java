package org.ost.attachment.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.IFrame;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import org.ost.attachment.entities.Attachment;
import org.ost.platform.attachment.model.MediaContentType;
import org.ost.attachment.util.MediaContentTypeUtil;
import org.ost.attachment.util.YoutubeUtil;
import org.springframework.context.annotation.Scope;

import java.util.List;

@SpringComponent
@Scope("prototype")
public class CardMediaLightbox {

    public void open(List<Attachment> attachments, int startIndex) {
        if (attachments.isEmpty()) return;

        int[] idx = { startIndex };

        Image mainImg = new Image();
        mainImg.addClassName("card-lightbox__main-image");

        IFrame iframe = new IFrame();
        iframe.addClassName("card-lightbox__iframe");
        iframe.getElement().setAttribute("allow",
                "accelerometer; autoplay; clipboard-write; encrypted-media; " +
                "gyroscope; picture-in-picture");
        iframe.getElement().setAttribute("allowfullscreen", "true");
        iframe.getElement().setAttribute("sandbox",
                "allow-scripts allow-same-origin allow-presentation");
        iframe.setVisible(false);

        com.vaadin.flow.dom.Element videoEl = new com.vaadin.flow.dom.Element("video");
        videoEl.setAttribute("controls", "");
        videoEl.getClassList().add("card-lightbox__main-video");
        Div mainVideo = new Div();
        mainVideo.addClassName("card-lightbox__main-video-wrapper");
        mainVideo.getElement().appendChild(videoEl);
        mainVideo.setVisible(false);

        Button prev = new Button(VaadinIcon.ANGLE_LEFT.create());
        Button next = new Button(VaadinIcon.ANGLE_RIGHT.create());
        prev.addClassName("card-lightbox__nav");
        next.addClassName("card-lightbox__nav");

        HorizontalLayout viewer = new HorizontalLayout(prev, mainImg, iframe, mainVideo, next);
        viewer.addClassName("card-lightbox__viewer");
        viewer.setAlignItems(FlexComponent.Alignment.CENTER);
        viewer.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        Div strip = new Div();
        strip.addClassName("card-lightbox__strip");

        for (int i = 0; i < attachments.size(); i++) {
            final int fi = i;
            Attachment a = attachments.get(i);
            Image thumb = new Image(thumbSrc(a), a.getFilename());
            thumb.addClassName("card-lightbox__thumb");
            thumb.addClickListener(_ -> {
                idx[0] = fi;
                update(mainImg, iframe, mainVideo, videoEl, strip, attachments, idx[0]);
            });
            strip.add(thumb);
        }

        Dialog dialog = new Dialog();
        dialog.addClassName("card-lightbox");
        dialog.setDraggable(false);
        dialog.setResizable(false);
        // Without this listener Vaadin auto-closes; with it, we must call dialog.close() explicitly.
        dialog.addDialogCloseActionListener(e -> dialog.close());

        prev.addClickListener(_ -> {
            idx[0] = (idx[0] - 1 + attachments.size()) % attachments.size();
            update(mainImg, iframe, mainVideo, videoEl, strip, attachments, idx[0]);
        });
        next.addClickListener(_ -> {
            idx[0] = (idx[0] + 1) % attachments.size();
            update(mainImg, iframe, mainVideo, videoEl, strip, attachments, idx[0]);
        });

        prev.setVisible(attachments.size() > 1);
        next.setVisible(attachments.size() > 1);

        VerticalLayout content = new VerticalLayout(viewer, strip);
        content.addClassName("card-lightbox__content");
        content.setPadding(false);
        content.setAlignItems(FlexComponent.Alignment.CENTER);

        Button closeBtn = new Button(VaadinIcon.CLOSE.create(), _ -> dialog.close());
        closeBtn.addClassName("card-lightbox__close");
        dialog.add(closeBtn, content);
        update(mainImg, iframe, mainVideo, videoEl, strip, attachments, idx[0]);
        dialog.open();
        dialog.getElement().executeJs(
            "this.addEventListener('opened-changed', (e) => {" +
            "  if (!e.detail.value) {" +
            "    const v = document.querySelector('.card-lightbox__main-video');" +
            "    if (v) v.pause();" +
            "    const f = document.querySelector('.card-lightbox__iframe');" +
            "    if (f) f.src = 'about:blank';" +
            "  }" +
            "});"
        );
    }

    private static String embedSrc(Attachment a) {
        if (MediaContentType.YOUTUBE.value().equals(a.getContentType())) return YoutubeUtil.embedUrl(YoutubeUtil.extractId(a.getUrl()));
        return a.getUrl();
    }

    private static String thumbSrc(Attachment a) {
        if (MediaContentType.YOUTUBE.value().equals(a.getContentType())) return YoutubeUtil.thumbnailUrl(YoutubeUtil.extractId(a.getUrl()));
        if (MediaContentTypeUtil.isVideo(a.getContentType()))               return AttachmentGallery.VIDEO_PLACEHOLDER_SVG;
        return a.getUrl();
    }

    private void update(Image mainImg, IFrame iframe, Div mainVideo,
                        com.vaadin.flow.dom.Element videoEl,
                        Div strip, List<Attachment> attachments, int idx) {
        Attachment a  = attachments.get(idx);
        String     ct = a.getContentType();

        if (MediaContentTypeUtil.isEmbedded(ct)) {
            UI.getCurrent().getPage().executeJs(
                "var v=document.querySelector('.card-lightbox__main-video'); if(v){v.pause();v.src='';}");
            String embedUrl = embedSrc(a);
            iframe.getElement().setAttribute("src", embedUrl);
            UI.getCurrent().getPage().executeJs(
                "var f=document.querySelector('.card-lightbox__iframe'); if(f) f.src=$0;", embedUrl);
            mainImg.setVisible(false);
            mainVideo.setVisible(false);
            iframe.setVisible(true);
        } else if (MediaContentTypeUtil.isUploadedVideo(ct)) {
            iframe.getElement().setAttribute("src", "about:blank");
            UI.getCurrent().getPage().executeJs(
                "var f=document.querySelector('.card-lightbox__iframe'); if(f) f.src='about:blank';");
            videoEl.setAttribute("src", a.getUrl());
            mainImg.setVisible(false);
            iframe.setVisible(false);
            mainVideo.setVisible(true);
            UI.getCurrent().getPage().executeJs(
                "var v=document.querySelector('.card-lightbox__main-video'); if(v){v.src=$0; v.load();}", a.getUrl());
        } else {
            iframe.getElement().setAttribute("src", "about:blank");
            UI.getCurrent().getPage().executeJs(
                "var f=document.querySelector('.card-lightbox__iframe'); if(f) f.src='about:blank';" +
                "var v=document.querySelector('.card-lightbox__main-video'); if(v){v.pause();v.src='';}");
            mainVideo.setVisible(false);
            iframe.setVisible(false);
            mainImg.setSrc(a.getUrl());
            mainImg.setAlt(a.getFilename());
            mainImg.setVisible(true);
        }

        strip.getChildren().forEach(c -> c.getElement().getClassList().remove("card-lightbox__thumb--active"));
        strip.getComponentAt(idx).getElement().getClassList().add("card-lightbox__thumb--active");
    }
}
