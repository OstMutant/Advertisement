package org.ost.advertisement.ui.views.main.tabs.users.overlay.modes;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.advertisement.common.I18nKey;
import org.ost.advertisement.events.dto.ActivityItemDto;
import org.ost.advertisement.events.model.ActionType;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.events.model.ChangeEntry;
import org.ost.advertisement.security.AccessEvaluator;
import org.ost.advertisement.dto.UserSettings;
import org.ost.advertisement.services.ActivityService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.SnapshotService;
import org.ost.advertisement.services.UserSettingsService;
import org.ost.advertisement.ui.views.utils.ActivityUiUtil;
import org.ost.advertisement.ui.views.utils.TimeZoneUtil;
import org.ost.advertisement.ui.views.components.buttons.UiIconButton;
import org.ost.advertisement.ui.views.components.fields.UiLabeledField;
import org.ost.advertisement.ui.views.components.buttons.UiPrimaryButton;
import org.ost.advertisement.ui.views.components.overlay.OverlayModeHandler;
import org.ost.advertisement.ui.views.components.overlay.OverlayLayout;
import org.ost.advertisement.ui.views.rules.Configurable;
import org.ost.advertisement.ui.views.rules.ComponentBuilder;
import org.ost.advertisement.ui.views.rules.I18nParams;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import java.util.List;
import java.util.function.Consumer;

import static org.ost.advertisement.common.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class UserViewOverlayModeHandler implements OverlayModeHandler,
        Configurable<UserViewOverlayModeHandler, UserViewOverlayModeHandler.Parameters>, I18nParams {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull User             user;
        @NonNull Runnable         onEdit;
        @NonNull Runnable         onClose;
        @NonNull Consumer<Long>   onRestoreUser;
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder extends ComponentBuilder<UserViewOverlayModeHandler, Parameters> {
        @Getter
        private final ObjectProvider<UserViewOverlayModeHandler> provider;
    }

    private final AccessEvaluator          access;
    @Getter
    private final I18nService              i18nService;
    private final ActivityService          activityService;
    private final SnapshotService          snapshotService;
    private final UserSettingsService      userSettingsService;
    private final ActivityUiUtil           activityUiUtil;
    private final UiLabeledField.Builder   labeledFieldBuilder;
    private final UiPrimaryButton.Builder  editButtonBuilder;
    private final UiIconButton.Builder     closeButtonBuilder;

    private Parameters params;

    @Override
    public UserViewOverlayModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    @Override
    public void activate(OverlayLayout layout) {
        User user = params.getUser();

        // Avatar with initials
        String initials = user.getName() != null && !user.getName().isBlank()
                ? user.getName().substring(0, Math.min(2, user.getName().length())).toUpperCase()
                : "?";
        Div avatar = new Div(new Span(initials));
        avatar.addClassName("user-view-avatar");

        // Name, email, role badge
        H2 nameHeading = new H2(user.getName());
        nameHeading.addClassName("user-view-name");

        Span emailSpan = new Span(user.getEmail());
        emailSpan.addClassName("user-view-email");

        Span roleBadge = new Span(user.getRole().name());
        roleBadge.addClassName("user-role-badge");
        roleBadge.addClassName("user-role-" + user.getRole().name().toLowerCase());

        Div nameBlock = new Div(nameHeading, emailSpan, roleBadge);
        nameBlock.addClassName("user-view-name-block");

        Div profileRow = new Div(avatar, nameBlock);
        profileRow.addClassName("user-view-profile-row");

        // Meta fields
        UiLabeledField idField      = field(USER_DIALOG_FIELD_ID_LABEL,      String.valueOf(user.getId()));
        UiLabeledField createdField = field(USER_DIALOG_FIELD_CREATED_LABEL,  TimeZoneUtil.formatInstantHuman(user.getCreatedAt()));
        UiLabeledField updatedField = field(USER_DIALOG_FIELD_UPDATED_LABEL,  TimeZoneUtil.formatInstantHuman(user.getUpdatedAt()));

        Div metaRow = new Div(idField, createdField, updatedField);
        metaRow.addClassName("user-view-meta-row");

        Div cardHeader = new Div(VaadinIcon.USER.create(), new Span(getValue(USER_DIALOG_SECTION_VIEW)));
        cardHeader.addClassName("overlay__view-card-header");

        Div card = new Div(cardHeader, profileRow, metaRow);
        card.addClassName("user-view-card");

        // ── Activity tab (lazy) ───────────────────────────────────────────
        Div activityContent = new Div();
        activityContent.addClassName("user-activity-content");
        activityContent.setVisible(false);

        Tab profileTab  = new Tab(getValue(ACTIVITY_PROFILE_TAB));
        Tab activityTab = new Tab(getValue(ACTIVITY_TAB));
        Tabs tabs = new Tabs(profileTab, activityTab);
        tabs.addClassName("user-view-tabs");

        tabs.addSelectedChangeListener(event -> {
            boolean isProfile = event.getSelectedTab() == profileTab;
            card.setVisible(isProfile);
            activityContent.setVisible(!isProfile);
            if (!isProfile && activityContent.getChildren().findFirst().isEmpty()) {
                activityContent.add(buildActivityContent(user));
            }
        });

        UiPrimaryButton editButton = editButtonBuilder.build(
                UiPrimaryButton.Parameters.builder().labelKey(USER_VIEW_BUTTON_EDIT).build());
        UiIconButton closeButton = closeButtonBuilder.build(
                UiIconButton.Parameters.builder()
                        .labelKey(MAIN_TAB_USERS)
                        .icon(VaadinIcon.CLOSE.create())
                        .build());

        editButton.addClickListener(_  -> params.getOnEdit().run());
        closeButton.addClickListener(_ -> params.getOnClose().run());
        editButton.setVisible(access.canOperate(user));

        layout.setContent(new Div(tabs, card, activityContent));
        layout.setHeaderActions(new Div(editButton, closeButton));
    }

    private Div buildActivityContent(User user) {
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

        for (ActivityItemDto item : items) {
            Div row = new Div();
            row.addClassName("user-activity-row");
            if (!item.entityExists()) row.addClassName("user-activity-row--deleted");

            boolean isSettingChange = "USER".equals(item.entityType())
                    && item.changes().stream().anyMatch(e -> e instanceof ChangeEntry.SettingChange);

            Span action = new Span(formatAction(item.actionType()));
            action.addClassName("user-activity-action");

            String typeLabel  = isSettingChange ? "SETTINGS" : item.entityType();
            String typeCssKey = isSettingChange ? "settings"  : item.entityType().toLowerCase();
            Span type = new Span(typeLabel);
            type.addClassName("user-activity-type");
            type.addClassName("user-activity-type--" + typeCssKey);

            String nameText;
            if ("ADVERTISEMENT".equals(item.entityType())) {
                nameText = item.changedByName() != null ? item.changedByName() : item.displayName();
            } else {
                nameText = item.entityExists()
                        ? item.displayName()
                        : item.displayName() + " " + getValue(ACTIVITY_ENTITY_DELETED);
            }
            Span name = new Span(nameText);
            name.addClassName("user-activity-name");

            Span time = new Span(TimeZoneUtil.formatInstantHuman(item.createdAt()));
            time.addClassName("user-activity-time");

            row.add(action, type, name, time);

            Span editor = ActivityUiUtil.buildEditorBadge(item.changedByUserId(), item.changedByName(), userId);
            if (editor != null) row.add(editor);

            if (isSettingChange) {
                row.add(activityUiUtil.buildChangesList(item.changes(), "user-activity-changes"));
                if (item.snapshotId() != null && item.snapshotId() > 0) {
                    snapshotService.getSettingsFromSnapshot(item.snapshotId()).ifPresent(snap -> {
                        boolean matchesCurrent = snap.getAdsPageSize() == currentSettings.getAdsPageSize()
                                && snap.getUsersPageSize() == currentSettings.getUsersPageSize();
                        if (matchesCurrent) {
                            Span badge = new Span(getValue(USER_ACTIVITY_CURRENT_STATE));
                            badge.addClassName("user-activity-current-badge");
                            row.add(badge);
                        }
                    });
                }
            } else if ("USER".equals(item.entityType())) {
                row.add(buildFullUserFieldsList(item, user));
                boolean isUserRow = item.snapshotId() != null && item.snapshotId() > 0;
                if (isUserRow && (item.actionType() != ActionType.CREATED || items.size() > 1)) {
                    boolean matchesCurrent = snapshotService.getUserStateAt(item.snapshotId())
                            .map(state -> state.name().equals(user.getName()) && state.role() == user.getRole())
                            .orElse(false);
                    if (matchesCurrent) {
                        Span badge = new Span(getValue(USER_ACTIVITY_CURRENT_STATE));
                        badge.addClassName("user-activity-current-badge");
                        row.add(badge);
                    } else {
                        Button restoreBtn = new Button(getValue(USER_RESTORE_BUTTON));
                        restoreBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                        restoreBtn.addClassName("adv-history-restore-btn");
                        restoreBtn.addClickListener(_ -> params.getOnRestoreUser().accept(item.snapshotId()));
                        row.add(restoreBtn);
                    }
                }
            } else if ("ADVERTISEMENT".equals(item.entityType())) {
                row.add(buildFullAdvFieldsList(item));
            }

            container.add(row);
        }
        return container;
    }

    private Div buildFullAdvFieldsList(ActivityItemDto item) {
        Div container = new Div();
        container.addClassName("user-activity-changes");

        boolean titleInChanges = false;
        boolean descInChanges  = false;
        boolean photoInChanges = false;

        for (ChangeEntry entry : item.changes()) {
            switch (entry) {
                case ChangeEntry.FieldChange f when "title".equals(f.field())       -> titleInChanges = true;
                case ChangeEntry.FieldChange f when "description".equals(f.field()) -> descInChanges  = true;
                case ChangeEntry.GenericChange ignored                                 -> photoInChanges = true;
                default -> {}
            }
            String text = activityUiUtil.format(entry);
            if (text != null && !text.isBlank()) {
                Span span = new Span("• " + text);
                span.addClassName("user-activity-changes-item");
                container.add(span);
            }
        }

        if (!titleInChanges && item.snapshotTitle() != null) {
            Span span = new Span("• " + getValue(I18nKey.CHANGES_FIELD_TITLE) + ": " + item.snapshotTitle());
            span.addClassNames("user-activity-changes-item", "user-activity-changes-item--unchanged");
            container.add(span);
        }
        if (!descInChanges && item.snapshotDescription() != null) {
            String desc = item.snapshotDescription().length() > 60
                    ? item.snapshotDescription().substring(0, 60) + "…" : item.snapshotDescription();
            Span span = new Span("• " + getValue(I18nKey.CHANGES_FIELD_DESCRIPTION) + ": " + desc);
            span.addClassNames("user-activity-changes-item", "user-activity-changes-item--unchanged");
            container.add(span);
        }
        return container;
    }

    private Div buildFullUserFieldsList(ActivityItemDto item, User user) {
        Div container = new Div();
        container.addClassName("user-activity-changes");

        boolean nameInChanges  = false;
        boolean emailInChanges = false;
        boolean roleInChanges  = false;

        for (ChangeEntry entry : item.changes()) {
            switch (entry) {
                case ChangeEntry.FieldChange f when "name".equals(f.field())  -> nameInChanges  = true;
                case ChangeEntry.FieldChange f when "email".equals(f.field()) -> emailInChanges = true;
                case ChangeEntry.FieldChange f when "role".equals(f.field())  -> roleInChanges  = true;
                default -> {}
            }
            String text = activityUiUtil.format(entry);
            if (text != null && !text.isBlank()) {
                Span span = new Span("• " + text);
                span.addClassName("user-activity-changes-item");
                container.add(span);
            }
        }

        if (!nameInChanges && user.getName() != null) {
            Span span = new Span("• " + getI18nService().get("changes.field.name") + ": " + user.getName());
            span.addClassNames("user-activity-changes-item", "user-activity-changes-item--unchanged");
            container.add(span);
        }
        if (!emailInChanges && user.getEmail() != null) {
            Span span = new Span("• " + getI18nService().get("changes.field.email") + ": " + user.getEmail());
            span.addClassNames("user-activity-changes-item", "user-activity-changes-item--unchanged");
            container.add(span);
        }
        if (!roleInChanges && user.getRole() != null) {
            Span span = new Span("• " + getI18nService().get("changes.field.role") + ": " + user.getRole().name());
            span.addClassNames("user-activity-changes-item", "user-activity-changes-item--unchanged");
            container.add(span);
        }
        return container;
    }

    private UiLabeledField field(I18nKey labelKey, String value) {
        return labeledFieldBuilder.build(
                UiLabeledField.Parameters.builder()
                        .labelKey(labelKey)
                        .value(value)
                        .build());
    }
}
