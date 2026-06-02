package org.ost.marketplace.spi;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.common.I18nKey;
import org.ost.platform.audit.dto.AuditHistoryItemDto;
import org.ost.platform.audit.spi.HistoryRowActionsHook;
import org.ost.platform.core.i18n.I18nService;
import com.vaadin.flow.spring.annotation.SpringComponent;

import java.util.function.ObjLongConsumer;

@SpringComponent
@RequiredArgsConstructor
public class AuditHistoryRowActionsHookImpl implements HistoryRowActionsHook {

    private final I18nService i18n;

    @Override
    public Component buildRowActions(AuditHistoryItemDto item, boolean isCurrentState,
                                     ObjLongConsumer<AuditHistoryItemDto> onRestore) {
        if (isCurrentState) {
            Span badge = new Span(i18n.get(I18nKey.AUDIT_HISTORY_CURRENT_STATE));
            badge.addClassName("entity-history-current-badge");
            return badge;
        }
        Button btn = new Button(i18n.get(I18nKey.AUDIT_HISTORY_RESTORE));
        btn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        btn.addClassName("entity-history-restore-btn");
        long snapId = item.snapshotId();
        btn.addClickListener(_ -> onRestore.accept(item, snapId));
        return btn;
    }
}
