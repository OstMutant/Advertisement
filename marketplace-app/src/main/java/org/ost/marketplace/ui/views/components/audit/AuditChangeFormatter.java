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

    static final int VALUE_COLLAPSE_THRESHOLD = 150;

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
            buildEntryInto(container, entry, cssClass, false);
        }
        return container;
    }

    void buildEntryInto(@NonNull Div container, @NonNull ChangeEntry entry, @NonNull String cssClass, boolean unchanged) {
        switch (entry) {
            case ChangeEntry.FieldChange(var field, var from, var to) -> {
                Div item = new Div();
                item.addClassName(cssClass + "-item");
                if (unchanged) item.addClassName(cssClass + "-item--unchanged");
                if (from == null || from.isBlank()) {
                    item.add(new Span("\u2022 " + field + ": \""));
                    addValueSection(item, cssClass, to);
                    item.add(new Span("\""));
                } else {
                    item.add(new Span("\u2022 " + field + ": \""));
                    addValueSection(item, cssClass, from);
                    item.add(new Span("\" \u2192 \""));
                    addValueSection(item, cssClass, to);
                    item.add(new Span("\""));
                }
                container.add(item);
            }
            case ChangeEntry.MediaChange _ -> {
                String text = format(entry);
                if (text == null || text.isBlank()) return;
                Div item = new Div();
                item.addClassName(cssClass + "-item");
                item.getElement().setProperty("innerHTML", i18n.get(I18nKey.AUDIT_CHANGES_BULLET, text));
                container.add(item);
            }
        }
    }

    private void addValueSection(@NonNull Div parent, @NonNull String cssClass, String value) {
        String rendered = trunc(value);
        boolean isLong  = value != null && !value.isBlank() && value.length() > VALUE_COLLAPSE_THRESHOLD;
        if (isLong) {
            Div valueDiv = new Div();
            valueDiv.addClassName(cssClass + "-value--collapsible");
            valueDiv.getElement().setProperty("innerHTML", rendered);
            Span toggle = buildValueToggle(valueDiv, cssClass);
            parent.add(valueDiv);
            parent.add(toggle);
            valueDiv.addAttachListener(_ ->
                valueDiv.getElement().executeJs(
                    "requestAnimationFrame(() => { if (this.scrollHeight > this.clientHeight) $0.style.display = 'block'; })",
                    toggle.getElement()
                )
            );
        } else {
            Span valueSpan = new Span();
            valueSpan.getElement().setProperty("innerHTML", rendered);
            parent.add(valueSpan);
        }
    }

    private Span buildValueToggle(@NonNull Div valueDiv, @NonNull String cssClass) {
        Span toggle = new Span(i18n.get(I18nKey.AUDIT_CHANGES_SHOW_MORE));
        toggle.addClassName(cssClass + "-value-toggle");
        boolean[] collapsed = {true};
        toggle.addClickListener(e -> {
            if (collapsed[0]) {
                valueDiv.removeClassName(cssClass + "-value--collapsible");
                toggle.setText(i18n.get(I18nKey.AUDIT_CHANGES_SHOW_LESS));
            } else {
                valueDiv.addClassName(cssClass + "-value--collapsible");
                toggle.setText(i18n.get(I18nKey.AUDIT_CHANGES_SHOW_MORE));
            }
            collapsed[0] = !collapsed[0];
        });
        return toggle;
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
        if (s == null || s.isBlank()) return "\u2014";
        if (s.length() <= 600) return s;
        return i18n.get(I18nKey.AUDIT_VALUE_TRUNCATED, s.substring(0, 600));
    }
}
