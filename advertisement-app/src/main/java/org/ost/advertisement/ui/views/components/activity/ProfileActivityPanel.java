package org.ost.advertisement.ui.views.components.activity;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.UserSettings;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.events.dto.ActivityItemDto;
import org.ost.advertisement.events.model.ActionType;
import org.ost.advertisement.services.ActivityService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.UserSettingsService;
import org.ost.advertisement.services.audit.AuditQueryService;
import org.ost.advertisement.ui.views.rules.I18nParams;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import java.util.List;
import java.util.function.Consumer;

import static org.ost.advertisement.common.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class ProfileActivityPanel implements I18nParams {

    @Getter private final I18nService                            i18nService;
    private final        ActivityService                         activityService;
    private final        AuditQueryService                       auditQueryService;
    private final        UserSettingsService                     userSettingsService;
    private final        ObjectProvider<ActivityRowRenderer>     rendererProvider;

    public Div build(User user, Consumer<Long> onRestoreUser, Consumer<UserSettings> onRestoreSettings) {
        Long userId = user.getId();
        List<ActivityItemDto> items = activityService.getForUser(userId);
        Div container = new Div();
        container.addClassName("user-activity-list");

        if (items.isEmpty()) {
            Span empty = new Span(getValue(ACTIVITY_EMPTY));
            empty.addClassName("user-activity-empty");
            container.add(empty);
            return container;
        }

        UserSettings currentSettings = userSettingsService.load(userId);
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
                        row.add(restoreBtn(getValue(SETTINGS_RESTORE_BUTTON), () -> onRestoreSettings.accept(snapSettings)));
                    }
                });
            } else if ("USER".equals(item.entityType()) && item.snapshotId() != null && item.snapshotId() > 0
                    && (item.actionType() != ActionType.CREATED || items.size() > 1)) {
                boolean matchesCurrent = auditQueryService.getUserStateAt(item.snapshotId())
                        .map(state -> state.name().equals(user.getName()) && state.role() == user.getRole())
                        .orElse(false);
                if (matchesCurrent) {
                    row.add(currentBadge());
                } else if (onRestoreUser != null) {
                    row.add(restoreBtn(getValue(USER_RESTORE_BUTTON), () -> onRestoreUser.accept(item.snapshotId())));
                }
            }

            container.add(row);
        }
        return container;
    }

    private Span currentBadge() {
        Span badge = new Span(getValue(USER_ACTIVITY_CURRENT_STATE));
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
