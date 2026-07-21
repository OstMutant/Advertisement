package org.ost.marketplace.ui.views.utils;

import com.vaadin.flow.component.UI;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BeforeUnloadUtil {

    public static void sync(boolean hasUnsavedChanges) {
        UI ui = UI.getCurrent();
        if (ui == null) return;
        ui.getPage().executeJs(
                "if (!window.__unsavedGuard) { window.__unsavedGuard = function(e) { e.preventDefault(); e.returnValue = ''; }; }"
                        + "window.removeEventListener('beforeunload', window.__unsavedGuard);"
                        + "if ($0) window.addEventListener('beforeunload', window.__unsavedGuard);",
                hasUnsavedChanges
        );
    }
}
