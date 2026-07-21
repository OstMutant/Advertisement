package org.ost.marketplace.ui.views.components.overlay;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.ShortcutRegistration;
import com.vaadin.flow.component.Shortcuts;
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

    private static final String FOCUSABLE_SELECTOR =
            "input, textarea, select, button, [tabindex]:not([tabindex='-1']), a[href]";

    protected void open() {
        getUI().ifPresent(ui -> ui.getPage().executeJs(
                "var el = $0;" +
                        "var y  = Math.round(window.scrollY);" +
                        "el.dataset.savedScroll       = y;" +
                        "document.body.style.position = 'fixed';" +
                        "document.body.style.top      = '-' + y + 'px';" +
                        "document.body.style.width    = '100%';" +
                        "el.__prevFocus = document.activeElement;" +
                        "el.__trapHandler = function(e) {" +
                        "  if (e.key !== 'Tab') return;" +
                        "  var f = Array.from(el.querySelectorAll($1)).filter(x => x.offsetParent !== null);" +
                        "  if (!f.length) return;" +
                        "  var first = f[0], last = f[f.length - 1];" +
                        "  if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }" +
                        "  else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }" +
                        "};" +
                        "el.addEventListener('keydown', el.__trapHandler);",
                getElement(), FOCUSABLE_SELECTOR
        ));
        addClassName("overlay--visible");
        getUI().ifPresent(ui -> escShortcut = Shortcuts.addShortcutListener(ui, this::onEsc, Key.ESCAPE));
    }

    protected void closeToList() {
        removeClassName("overlay--visible");
        unregisterEsc();
        getUI().ifPresent(ui -> ui.getPage().executeJs(
                "var y = parseInt($0.dataset.savedScroll || '0', 10);" +
                        "document.body.style.position = '';" +
                        "document.body.style.top      = '';" +
                        "document.body.style.width    = '';" +
                        "window.scrollTo(0, y);" +
                        "if ($0.__trapHandler) { $0.removeEventListener('keydown', $0.__trapHandler); $0.__trapHandler = null; }" +
                        "if ($0.__prevFocus) { $0.__prevFocus.focus(); $0.__prevFocus = null; }",
                getElement()
        ));
    }

    private void unregisterEsc() {
        if (escShortcut != null) {
            escShortcut.remove();
            escShortcut = null;
        }
    }
}