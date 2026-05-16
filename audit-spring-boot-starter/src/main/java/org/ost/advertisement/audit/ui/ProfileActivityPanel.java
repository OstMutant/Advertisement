package org.ost.advertisement.audit.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.advertisement.audit.api.ConditionalOnAuditEnabled;
import org.ost.advertisement.audit.services.ActivityService;
import org.ost.advertisement.audit.services.AuditQueryService;
import org.ost.advertisement.core.config.UserSettings;
import org.ost.advertisement.core.model.Role;
import org.ost.advertisement.audit.dto.ActivityItemDto;
import org.ost.advertisement.core.model.ActionType;
import org.ost.advertisement.core.model.EntityType;
import org.ost.advertisement.core.i18n.I18nService;
import org.ost.advertisement.core.ui.Configurable;
import org.ost.advertisement.core.ui.ComponentBuilder;
import org.ost.advertisement.core.ui.Initialization;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import java.util.List;
import java.util.function.Consumer;

@CssImport("./user-activity.css")
@ConditionalOnAuditEnabled
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class ProfileActivityPanel extends Div
        implements Configurable<ProfileActivityPanel, ProfileActivityPanel.Parameters>,
                   Initialization<ProfileActivityPanel> {

    @Value
    @lombok.Builder
    public static class Parameters {
        Long userId;
        String userName;
        Role userRole;
        UserSettings currentSettings;
        Consumer<Long> onRestoreUser;
        Consumer<UserSettings> onRestoreSettings;
    }

    @ConditionalOnAuditEnabled
    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder extends ComponentBuilder<ProfileActivityPanel, Parameters> {
        @Getter
        private final ObjectProvider<ProfileActivityPanel> provider;
    }

    private final I18nService                        i18n;
    private final ActivityService                    activityService;
    private final AuditQueryService                  auditQueryService;
    private final ObjectProvider<ActivityRowRenderer> rendererProvider;

    @Override
    @PostConstruct
    public ProfileActivityPanel init() {
        addClassName("user-activity-list");
        return this;
    }

    @Override
    @SuppressWarnings("java:S4276")
    public ProfileActivityPanel configure(Parameters p) {
        List<ActivityItemDto> items = activityService.getForUser(p.getUserId());

        if (items.isEmpty()) {
            Span empty = new Span(i18n.get(AuditMessages.ACTIVITY_EMPTY));
            empty.addClassName("user-activity-empty");
            add(empty);
            return this;
        }

        ActivityRowRenderer renderer = rendererProvider.getObject();
        for (ActivityItemDto item : items) {
            Div row = renderer.buildRow(item, p.getUserId());
            addSettingsRestore(row, item, renderer, p.getCurrentSettings(), p.getOnRestoreSettings());
            addUserRestore(row, item, p.getUserName(), p.getUserRole(), p.getOnRestoreUser(), items.size());
            add(row);
        }
        return this;
    }

    private void addSettingsRestore(Div row, ActivityItemDto item, ActivityRowRenderer renderer,
                                     UserSettings currentSettings, Consumer<UserSettings> onRestoreSettings) {
        if (!ActivityRowRenderer.isSettingChange(item) || item.snapshotId() == null || item.snapshotId() <= 0) return;
        auditQueryService.getSettingsFromSnapshot(item.snapshotId()).ifPresent(snapSettings -> {
            row.add(renderer.buildSettingsFieldsList(item, snapSettings));
            boolean matchesCurrent = snapSettings.getAdsPageSize() == currentSettings.getAdsPageSize()
                    && snapSettings.getUsersPageSize() == currentSettings.getUsersPageSize();
            if (matchesCurrent) {
                row.add(currentBadge());
            } else if (onRestoreSettings != null) {
                row.add(restoreBtn(i18n.get(AuditMessages.SETTINGS_RESTORE_BUTTON), () -> onRestoreSettings.accept(snapSettings)));
            }
        });
    }

    @SuppressWarnings("java:S4276")
    private void addUserRestore(Div row, ActivityItemDto item,
                                 String userName, Role userRole,
                                 Consumer<Long> onRestoreUser, int itemsSize) {
        if (item.entityType() != EntityType.USER) return;
        if (item.snapshotId() == null || item.snapshotId() <= 0) return;
        if (item.actionType() == ActionType.CREATED && itemsSize <= 1) return;
        boolean matchesCurrent = auditQueryService.getUserStateAt(item.snapshotId())
                .map(state -> state.name().equals(userName) && state.role() == userRole)
                .orElse(false);
        if (matchesCurrent) {
            row.add(currentBadge());
        } else if (onRestoreUser != null) {
            row.add(restoreBtn(i18n.get(AuditMessages.USER_RESTORE_BUTTON), () -> onRestoreUser.accept(item.snapshotId())));
        }
    }

    private Span currentBadge() {
        Span badge = new Span(i18n.get(AuditMessages.USER_ACTIVITY_CURRENT_STATE));
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
