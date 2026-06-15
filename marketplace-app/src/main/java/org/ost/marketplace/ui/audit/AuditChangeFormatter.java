package org.ost.marketplace.ui.audit;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.marketplace.i18n.I18nService;

import java.util.List;

@SpringComponent
@RequiredArgsConstructor
public class AuditChangeFormatter {

    private final I18nService i18n;

    public Span buildEditorBadge(Long changedByActorId, String changedByName, Long viewerActorId) {
        if (changedByActorId == null || changedByActorId == 0 || changedByActorId.equals(viewerActorId)) {
            return null;
        }
        Span badge = new Span(i18n.get(AuditI18n.CHANGES_EDITOR, changedByName));
        badge.addClassName("activity-feed-editor");
        return badge;
    }

    public Div buildChangesList(@NonNull List<ChangeEntry> changes, @NonNull String cssClass) {
        Div container = new Div();
        container.addClassName(cssClass);
        if (changes.isEmpty()) return container;
        for (ChangeEntry entry : changes) {
            String text = format(entry);
            if (text != null && !text.isBlank()) {
                Span item = new Span(i18n.get(AuditI18n.CHANGES_BULLET, text));
                item.addClassName(cssClass + "-item");
                container.add(item);
            }
        }
        return container;
    }

    public String format(@NonNull ChangeEntry entry) {
        return switch (entry) {
            case ChangeEntry.FieldChange(var field, var from, var to) -> {
                if (from == null || from.isBlank()) {
                    yield i18n.get(AuditI18n.CHANGES_SET, field, trunc(to));
                }
                yield i18n.get(AuditI18n.CHANGES_FIELD_CHANGED, field, trunc(from), trunc(to));
            }
            case ChangeEntry.MediaChange(var before, var after) -> {
                String label = i18n.get(AuditI18n.CHANGES_MEDIA);
                if (before == null || before.isBlank()) {
                    yield i18n.get(AuditI18n.CHANGES_SET, label, after);
                }
                yield i18n.get(AuditI18n.CHANGES_MEDIA_CHANGED, label, before, after);
            }
        };
    }

    private String trunc(String s) {
        if (s == null || s.length() <= 120) return s;
        return i18n.get(AuditI18n.VALUE_TRUNCATED, s.substring(0, 120));
    }
}
