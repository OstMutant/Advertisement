package org.ost.advertisement.ui.views.utils;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ActivityUiUtil {

    /** Returns a span showing "by <name>" when the editor is different from the viewer. */
    public static Span buildEditorBadge(Long changedByUserId, String changedByName, Long viewerUserId) {
        if (changedByUserId == null || changedByUserId == 0 || changedByUserId.equals(viewerUserId)) {
            return null;
        }
        Span badge = new Span("↳ " + changedByName);
        badge.addClassName("user-activity-editor");
        return badge;
    }

    public static Div buildChangesList(String changesSummary, String cssClass) {
        return buildChangesList(changesSummary, cssClass, false);
    }

    public static Div buildChangesList(String changesSummary, String cssClass, boolean textOnly) {
        Div container = new Div();
        container.addClassName(cssClass);
        for (String part : changesSummary.split("; ")) {
            if (part.isBlank()) continue;
            if (textOnly && (part.startsWith("видалено фото") || part.startsWith("додано фото"))) continue;
            Span item = new Span("• " + part);
            item.addClassName(cssClass + "-item");
            container.add(item);
        }
        return container;
    }
}
