package org.ost.advertisement.ui.views.components.overlay;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.ShortcutRegistration;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;

public abstract class BaseOverlay extends Div {

    private ShortcutRegistration escShortcut;
    private boolean              initialized = false;

    protected abstract void buildContent();

    protected abstract void onEsc();

    protected void ensureInitialized() {
        if (initialized) return;
        initialized = true;
        addClassName("base-overlay");
        buildContent();
    }

    protected void open() {
        UI.getCurrent().getPage().executeJs(
                "var el = $0;" +
                        "var y  = Math.round(window.scrollY);" +
                        "el.dataset.savedScroll       = y;" +
                        "document.body.style.position = 'fixed';" +
                        "document.body.style.top      = '-' + y + 'px';" +
                        "document.body.style.width    = '100%';",
                getElement()
        );
        addClassName("overlay--visible");
        escShortcut = Shortcuts.addShortcutListener(UI.getCurrent(), this::onEsc, Key.ESCAPE);
    }

    protected void closeToList() {
        removeClassName("overlay--visible");
        unregisterEsc();
        UI.getCurrent().getPage().executeJs(
                "var y = parseInt($0.dataset.savedScroll || '0', 10);" +
                        "document.body.style.position = '';" +
                        "document.body.style.top      = '';" +
                        "document.body.style.width    = '';" +
                        "window.scrollTo(0, y);",
                getElement()
        );
    }

    private void unregisterEsc() {
        if (escShortcut != null) {
            escShortcut.remove();
            escShortcut = null;
        }
    }
}