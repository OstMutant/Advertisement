package org.ost.marketplace.ui.views.utils;

import com.vaadin.flow.component.Component;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ShareUtil {

    public static void share(Component component, String url, String title, Runnable onCopied) {
        component.getElement().executeJs("""
                if (navigator.share) {
                    return navigator.share({ title: $0, url: $1 }).then(() => 'shared').catch(() => 'cancelled');
                }
                return navigator.clipboard.writeText($1).then(() => 'copied');
                """, title, url)
                .then(String.class, result -> {
                    if ("copied".equals(result)) onCopied.run();
                });
    }
}
