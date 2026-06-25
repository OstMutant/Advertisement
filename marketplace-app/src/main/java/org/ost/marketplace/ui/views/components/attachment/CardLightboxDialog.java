package org.ost.marketplace.ui.views.components.attachment;

import com.vaadin.flow.component.dialog.Dialog;

class CardLightboxDialog extends Dialog {

    CardLightboxDialog() {
        addClassName("card-lightbox");
        setDraggable(false);
        setResizable(false);
        addDialogCloseActionListener(_ -> close());
        getElement().executeJs(
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
}
