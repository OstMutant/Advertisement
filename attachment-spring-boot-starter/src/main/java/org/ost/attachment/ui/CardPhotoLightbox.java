package org.ost.attachment.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.IFrame;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import org.ost.attachment.entity.Attachment;
import org.ost.attachment.entity.MediaContentType;
import org.ost.attachment.util.YoutubeUtil;
import org.springframework.context.annotation.Scope;

import java.util.List;

@SpringComponent
@Scope("prototype")
public class CardPhotoLightbox {

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
                "allow-scripts allow-same-origin allow-presentation allow-forms");
        iframe.setVisible(false);

        Button prev = new Button(VaadinIcon.ANGLE_LEFT.create());
        Button next = new Button(VaadinIcon.ANGLE_RIGHT.create());
        prev.addClassName("card-lightbox__nav");
        next.addClassName("card-lightbox__nav");

        HorizontalLayout viewer = new HorizontalLayout(prev, mainImg, iframe, next);
        viewer.addClassName("card-lightbox__viewer");
        viewer.setAlignItems(HorizontalLayout.Alignment.CENTER);
        viewer.setJustifyContentMode(HorizontalLayout.JustifyContentMode.CENTER);

        Div strip = new Div();
        strip.addClassName("card-lightbox__strip");

        for (int i = 0; i < attachments.size(); i++) {
            final int fi = i;
            Attachment a = attachments.get(i);
            Image thumb = new Image(thumbSrc(a), a.getFilename());
            thumb.addClassName("card-lightbox__thumb");
            thumb.addClickListener(_ -> {
                idx[0] = fi;
                update(mainImg, iframe, strip, attachments, idx[0]);
            });
            strip.add(thumb);
        }

        Dialog dialog = new Dialog();
        dialog.addClassName("card-lightbox");
        dialog.setModal(true);
        dialog.setDraggable(false);
        dialog.setResizable(false);
        dialog.setCloseOnEsc(true);
        dialog.setCloseOnOutsideClick(true);
        dialog.addOpenedChangeListener(e -> { if (!e.isOpened()) iframe.getElement().setAttribute("src", "about:blank"); });

        prev.addClickListener(_ -> {
            idx[0] = (idx[0] - 1 + attachments.size()) % attachments.size();
            update(mainImg, iframe, strip, attachments, idx[0]);
        });
        next.addClickListener(_ -> {
            idx[0] = (idx[0] + 1) % attachments.size();
            update(mainImg, iframe, strip, attachments, idx[0]);
        });

        prev.setVisible(attachments.size() > 1);
        next.setVisible(attachments.size() > 1);

        VerticalLayout content = new VerticalLayout(viewer, strip);
        content.addClassName("card-lightbox__content");
        content.setPadding(false);
        content.setAlignItems(VerticalLayout.Alignment.CENTER);

        Button closeBtn = new Button(VaadinIcon.CLOSE.create(), _ -> dialog.close());
        closeBtn.addClassName("card-lightbox__close");
        dialog.add(closeBtn, content);
        update(mainImg, iframe, strip, attachments, idx[0]);
        dialog.open();
    }

    private static boolean isVideo(String contentType) {
        return MediaContentType.isVideo(contentType);
    }

    private static String embedSrc(Attachment a) {
        if (MediaContentType.YOUTUBE.value().equals(a.getContentType())) return YoutubeUtil.embedUrl(YoutubeUtil.extractId(a.getUrl()));
        return a.getUrl();
    }

    private static String thumbSrc(Attachment a) {
        if (MediaContentType.YOUTUBE.value().equals(a.getContentType())) return YoutubeUtil.thumbnailUrl(YoutubeUtil.extractId(a.getUrl()));
        if (MediaContentType.EMBED.value().equals(a.getContentType()))   return AttachmentGallery.VIDEO_PLACEHOLDER_SVG;
        return a.getUrl();
    }

    private void update(Image mainImg, IFrame iframe, Div strip,
                        List<Attachment> attachments, int idx) {
        Attachment a = attachments.get(idx);
        boolean isVideo = isVideo(a.getContentType());

        if (isVideo) {
            String embedUrl = embedSrc(a);
            iframe.getElement().setAttribute("src", embedUrl);
            UI.getCurrent().getPage().executeJs(
                "var f=document.querySelector('.card-lightbox__iframe'); if(f) f.src=$0;", embedUrl);
            mainImg.setVisible(false);
            iframe.setVisible(true);
        } else {
            iframe.getElement().setAttribute("src", "about:blank");
            UI.getCurrent().getPage().executeJs(
                "var f=document.querySelector('.card-lightbox__iframe'); if(f) f.src='about:blank';");
            iframe.setVisible(false);
            mainImg.setSrc(a.getUrl());
            mainImg.setAlt(a.getFilename());
            mainImg.setVisible(true);
        }

        strip.getChildren().forEach(c -> c.getElement().getClassList().remove("card-lightbox__thumb--active"));
        strip.getComponentAt(idx).getElement().getClassList().add("card-lightbox__thumb--active");
    }
}
