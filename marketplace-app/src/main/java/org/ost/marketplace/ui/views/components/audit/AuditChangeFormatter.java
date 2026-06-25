package org.ost.marketplace.ui.views.components.audit;
import org.ost.marketplace.services.i18n.I18nKey;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.marketplace.services.i18n.I18nService;

import java.util.List;

@SpringComponent
@RequiredArgsConstructor
public class AuditChangeFormatter {

    private final I18nService i18n;

    public Span buildEditorBadge(Long changedByActorId, String changedByName) {
        if (changedByActorId == null || changedByActorId == 0) {
            return null;
        }
        Span badge = new Span(i18n.get(I18nKey.AUDIT_CHANGES_EDITOR, changedByName));
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
                Span item = new Span(i18n.get(I18nKey.AUDIT_CHANGES_BULLET, text));
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
                    yield i18n.get(I18nKey.AUDIT_CHANGES_SET, field, trunc(to));
                }
                yield i18n.get(I18nKey.AUDIT_CHANGES_FIELD_CHANGED, field, trunc(from), trunc(to));
            }
            case ChangeEntry.MediaChange(var before, var after) -> {
                String label = i18n.get(I18nKey.AUDIT_CHANGES_MEDIA);
                if (before == null || before.isBlank()) {
                    yield i18n.get(I18nKey.AUDIT_CHANGES_SET, label, after);
                }
                yield i18n.get(I18nKey.AUDIT_CHANGES_MEDIA_CHANGED, label, before, after);
            }
        };
    }

    private String trunc(String s) {
        if (s == null || s.length() <= 120) return s;
        return i18n.get(I18nKey.AUDIT_VALUE_TRUNCATED, s.substring(0, 120));
    }
}
