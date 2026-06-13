package org.ost.ui.attachment;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.NonNull;
import org.ost.attachment.entities.Attachment;
import org.springframework.context.annotation.Scope;

import java.util.List;

@SpringComponent
@Scope("prototype")
public class CardMediaLightbox {

    public void open(@NonNull List<Attachment> attachments, int startIndex) {
        int[] idx = {startIndex};

        CardLightboxViewer viewer = new CardLightboxViewer();
        CardLightboxStrip  strip  = new CardLightboxStrip(attachments);
        strip.onSelect(fi -> { idx[0] = fi; navigate(viewer, strip, attachments, fi); });

        viewer.onPrev(() -> { idx[0] = (idx[0] - 1 + attachments.size()) % attachments.size(); navigate(viewer, strip, attachments, idx[0]); });
        viewer.onNext(() -> { idx[0] = (idx[0] + 1) % attachments.size();                      navigate(viewer, strip, attachments, idx[0]); });
        viewer.setNavVisible(attachments.size() > 1);

        VerticalLayout content = new VerticalLayout(viewer, strip);
        content.addClassName("card-lightbox__content");
        content.setPadding(false);
        content.setAlignItems(FlexComponent.Alignment.CENTER);

        CardLightboxDialog dialog = new CardLightboxDialog();
        Button closeBtn = new Button(VaadinIcon.CLOSE.create(), _ -> dialog.close());
        closeBtn.addClassName("card-lightbox__close");
        dialog.add(closeBtn, content);

        navigate(viewer, strip, attachments, idx[0]);
        dialog.open();
    }

    private static void navigate(CardLightboxViewer viewer, CardLightboxStrip strip,
                                  List<Attachment> attachments, int idx) {
        viewer.update(attachments.get(idx));
        strip.setActive(idx);
    }
}
