package org.ost.attachment.ui;

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
            String thumbSrc = "video/youtube".equals(a.getContentType())
                    ? YoutubeUtil.thumbnailUrl(YoutubeUtil.extractId(a.getUrl()))
                    : a.getUrl();
            Image thumb = new Image(thumbSrc, a.getFilename());
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
        dialog.addOpenedChangeListener(e -> { if (!e.isOpened()) iframe.setSrc(""); });

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

    private void update(Image mainImg, IFrame iframe, Div strip,
                        List<Attachment> attachments, int idx) {
        Attachment a = attachments.get(idx);
        boolean isVideo = "video/youtube".equals(a.getContentType());

        mainImg.setVisible(!isVideo);
        iframe.setVisible(isVideo);

        if (isVideo) {
            iframe.setSrc(YoutubeUtil.embedUrl(YoutubeUtil.extractId(a.getUrl())));
        } else {
            iframe.setSrc("");
            mainImg.setSrc(a.getUrl());
            mainImg.setAlt(a.getFilename());
        }

        strip.getChildren().forEach(c -> c.getElement().getClassList().remove("card-lightbox__thumb--active"));
        strip.getComponentAt(idx).getElement().getClassList().add("card-lightbox__thumb--active");
    }
}
