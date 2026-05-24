package org.ost.audit.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.RequiredArgsConstructor;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.i18n.I18nService;

import java.util.List;

@SpringComponent
@RequiredArgsConstructor
public class ActivityPanel {

    private final I18nService i18n;

    public static Span buildEditorBadge(Long changedByActorId, String changedByName, Long viewerActorId) {
        if (changedByActorId == null || changedByActorId == 0 || changedByActorId.equals(viewerActorId)) {
            return null;
        }
        Span badge = new Span("↳ " + changedByName);
        badge.addClassName("activity-feed-editor");
        return badge;
    }

    public Div buildChangesList(List<ChangeEntry> changes, String cssClass) {
        Div container = new Div();
        container.addClassName(cssClass);
        if (changes == null || changes.isEmpty()) return container;
        for (ChangeEntry entry : changes) {
            String text = format(entry);
            if (text != null && !text.isBlank()) {
                Span item = new Span("• " + text);
                item.addClassName(cssClass + "-item");
                container.add(item);
            }
        }
        return container;
    }

    public String format(ChangeEntry entry) {
        return switch (entry) {
            case ChangeEntry.FieldChange(var field, var from, var to) -> {
                String label = i18n.get(AuditMessages.fieldLabel(field));
                if (from == null || from.isBlank()) {
                    yield label + ": \"" + to + "\"";
                }
                yield label + ": \"" + from + "\" → \"" + to + "\"";
            }
            case ChangeEntry.SettingChange(var key, var from, var to) -> {
                String label = i18n.get(AuditMessages.settingLabel(key));
                if (from == null) yield label + ": " + to;
                yield label + ": " + from + " → " + to;
            }
            case ChangeEntry.NoteEntry(var text) -> text;
            case ChangeEntry.GenericChange(var labelI18nKey, var before, var after) -> {
                String label = i18n.get(labelI18nKey);
                if (before == null || before.isBlank()) {
                    yield label + ": \"" + after + "\"";
                }
                yield label + ": " + before + " → " + after;
            }
        };
    }
}
