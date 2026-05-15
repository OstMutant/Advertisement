package org.ost.advertisement.audit.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.events.model.ChangeEntry;
import org.ost.advertisement.i18n.I18nService;

import java.util.List;

@SpringComponent
@RequiredArgsConstructor
public class ActivityPanel {

    private final I18nService i18n;

    public static Span buildEditorBadge(Long changedByUserId, String changedByName, Long viewerUserId) {
        if (changedByUserId == null || changedByUserId == 0 || changedByUserId.equals(viewerUserId)) {
            return null;
        }
        Span badge = new Span("↳ " + changedByName);
        badge.addClassName("user-activity-editor");
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
            case ChangeEntry.FieldChange f -> {
                String label = i18n.get(AuditMessages.fieldLabel(f.field()));
                if (f.from() == null || f.from().isBlank()) {
                    yield label + ": \"" + f.to() + "\"";
                }
                yield label + ": \"" + f.from() + "\" → \"" + f.to() + "\"";
            }
            case ChangeEntry.SettingChange s -> {
                String label = i18n.get(AuditMessages.settingLabel(s.key()));
                yield label + ": " + s.from() + " → " + s.to();
            }
            case ChangeEntry.NoteEntry n -> n.text();
            case ChangeEntry.GenericChange g -> {
                String label = i18n.get(g.labelI18nKey());
                if (g.before() == null || g.before().isBlank()) {
                    yield label + ": \"" + g.after() + "\"";
                }
                yield label + ": " + g.before() + " → " + g.after();
            }
        };
    }
}
