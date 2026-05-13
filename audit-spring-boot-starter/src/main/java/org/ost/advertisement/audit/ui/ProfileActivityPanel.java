package org.ost.advertisement.audit.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.audit.services.ActivityService;
import org.ost.advertisement.audit.services.AuditQueryService;
import org.ost.advertisement.dto.UserSettings;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.events.dto.ActivityItemDto;
import org.ost.advertisement.events.model.ActionType;
import org.ost.advertisement.events.model.EntityType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Scope;

import java.util.List;
import java.util.function.Consumer;

@CssImport("./user-activity.css")
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class ProfileActivityPanel implements AuditI18nSupport {

    @Getter private final MessageSource                         messageSource;
    private final        ActivityService                        activityService;
    private final        AuditQueryService                      auditQueryService;
    private final        ObjectProvider<ActivityRowRenderer>    rendererProvider;

    public Div build(Long userId, String userName, Role userRole,
                     UserSettings currentSettings,
                     Consumer<Long> onRestoreUser,
                     Consumer<UserSettings> onRestoreSettings) {
        List<ActivityItemDto> items = activityService.getForUser(userId);
        Div container = new Div();
        container.addClassName("user-activity-list");

        if (items.isEmpty()) {
            Span empty = new Span(msg(AuditKeys.ACTIVITY_EMPTY));
            empty.addClassName("user-activity-empty");
            container.add(empty);
            return container;
        }

        ActivityRowRenderer renderer = rendererProvider.getObject();

        for (ActivityItemDto item : items) {
            Div row = renderer.buildRow(item, userId);

            if (ActivityRowRenderer.isSettingChange(item) && item.snapshotId() != null && item.snapshotId() > 0) {
                auditQueryService.getSettingsFromSnapshot(item.snapshotId()).ifPresent(snapSettings -> {
                    row.add(renderer.buildSettingsFieldsList(item, snapSettings));
                    boolean matchesCurrent = snapSettings.getAdsPageSize() == currentSettings.getAdsPageSize()
                            && snapSettings.getUsersPageSize() == currentSettings.getUsersPageSize();
                    if (matchesCurrent) {
                        row.add(currentBadge());
                    } else if (onRestoreSettings != null) {
                        row.add(restoreBtn(msg(AuditKeys.SETTINGS_RESTORE_BUTTON), () -> onRestoreSettings.accept(snapSettings)));
                    }
                });
            } else if (item.entityType() == EntityType.USER && item.snapshotId() != null && item.snapshotId() > 0
                    && (item.actionType() != ActionType.CREATED || items.size() > 1)) {
                boolean matchesCurrent = auditQueryService.getUserStateAt(item.snapshotId())
                        .map(state -> state.name().equals(userName) && state.role() == userRole)
                        .orElse(false);
                if (matchesCurrent) {
                    row.add(currentBadge());
                } else if (onRestoreUser != null) {
                    row.add(restoreBtn(msg(AuditKeys.USER_RESTORE_BUTTON), () -> onRestoreUser.accept(item.snapshotId())));
                }
            }

            container.add(row);
        }
        return container;
    }

    private Span currentBadge() {
        Span badge = new Span(msg(AuditKeys.USER_ACTIVITY_CURRENT_STATE));
        badge.addClassName("user-activity-current-badge");
        return badge;
    }

    private Button restoreBtn(String label, Runnable onClick) {
        Button btn = new Button(label);
        btn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        btn.addClassName("adv-history-restore-btn");
        btn.addClickListener(_ -> onClick.run());
        return btn;
    }
}
