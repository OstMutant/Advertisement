package org.ost.marketplace.ui.views.utils;

import com.vaadin.flow.component.UI;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Pushes a shareable URL into the browser's history and keeps the page's {@code <base>} tag in
 * sync -- without this, a relative Vaadin resource URL (e.g. a file upload) computed after the
 * push resolves against the stale, pre-push path depth and gets rejected by the server.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BrowserHistoryUtil {

    public static void pushStateWithBaseSync(@NonNull String path) {
        UI ui = UI.getCurrent();
        ui.getPage().getHistory().pushState(null, path);
        ui.getPage().executeJs("""
                const segments = $0.split('/').filter(Boolean);
                const depth = Math.max(0, segments.length - 1);
                const base = depth === 0 ? '.' : './' + '../'.repeat(depth - 1) + '..';
                document.querySelector('base').setAttribute('href', base);
                """, path);
    }
}
