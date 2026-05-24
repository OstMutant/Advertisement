package org.ost.marketplace.ui.views.main.tabs.users.overlay.modes;

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
import org.ost.marketplace.common.I18nKey;
import org.ost.marketplace.entities.User;
import org.ost.marketplace.security.AccessEvaluator;
import org.ost.platform.core.i18n.I18nService;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.components.fields.UiLabeledField;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.marketplace.ui.views.components.overlay.OverlayModeHandler;
import org.ost.marketplace.ui.views.components.overlay.OverlayLayout;
import org.ost.audit.ui.SnapshotBinder;
import org.ost.marketplace.entities.UserSettings;
import org.ost.marketplace.dto.audit.SettingsSnapshotDto;
import org.ost.marketplace.dto.audit.UserSnapshotDto;
import org.ost.platform.audit.spi.AuditUiPort;
import org.ost.platform.ui.Configurable;
import org.ost.platform.ui.ComponentBuilder;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.ost.marketplace.services.user.UserSettingsService;
import org.ost.query.ui.utils.TimeZoneUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import java.util.function.BiConsumer;

import static org.ost.marketplace.common.I18nKey.*;

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
        @NonNull BiConsumer<Long, Long> onRestoreUser;
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder extends ComponentBuilder<UserViewOverlayModeHandler, Parameters> {
        @Getter
        private final ObjectProvider<UserViewOverlayModeHandler> provider;
    }

    private final AccessEvaluator                            access;
    @Getter
    private final I18nService                                i18nService;
    private final UserSettingsService                        userSettingsService;
    private final UiLabeledField.Builder                     labeledFieldBuilder;
    private final UiPrimaryButton.Builder                    editButtonBuilder;
    private final UiIconButton.Builder                       closeButtonBuilder;
    private final ObjectProvider<AuditUiPort>             auditUiExtensionProvider;
    private final SnapshotBinder.Builder<UserSnapshotDto>         userBinderBuilder;
    private final SnapshotBinder.Builder<SettingsSnapshotDto>   settingsBinderBuilder;

    private Parameters params;

    @Override
    public UserViewOverlayModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    @Override
    public void activate(OverlayLayout layout) {
        User user = params.getUser();

        String initials = user.getName() != null && !user.getName().isBlank()
                ? user.getName().substring(0, Math.min(2, user.getName().length())).toUpperCase()
                : "?";
        Div avatar = new Div(new Span(initials));
        avatar.addClassName("user-view-avatar");

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

        UiLabeledField idField      = field(USER_DIALOG_FIELD_ID_LABEL,      String.valueOf(user.getId()));
        UiLabeledField createdField = field(USER_DIALOG_FIELD_CREATED_LABEL,  TimeZoneUtil.formatInstantHuman(user.getCreatedAt()));
        UiLabeledField updatedField = field(USER_DIALOG_FIELD_UPDATED_LABEL,  TimeZoneUtil.formatInstantHuman(user.getUpdatedAt()));

        Div metaRow = new Div(idField, createdField, updatedField);
        metaRow.addClassName("user-view-meta-row");

        Div cardHeader = new Div(VaadinIcon.USER.create(), new Span(getValue(USER_DIALOG_SECTION_VIEW)));
        cardHeader.addClassName("overlay__view-card-header");

        Div card = new Div(cardHeader, profileRow, metaRow);
        card.addClassName("user-view-card");

        Tab profileTab = new Tab(getValue(ACTIVITY_PROFILE_TAB));
        Tabs tabs = new Tabs(profileTab);
        tabs.addClassName("user-view-tabs");

        Div layoutContent;

        AuditUiPort auditUi = auditUiExtensionProvider.getIfAvailable();
        if (auditUi != null) {
            Div activityContent = new Div();
            activityContent.addClassName("activity-feed-content");
            activityContent.setVisible(false);

            Tab activityTab = new Tab(getValue(ACTIVITY_TAB));
            tabs.add(activityTab);

            tabs.addSelectedChangeListener(event -> {
                boolean isProfile = event.getSelectedTab() == profileTab;
                card.setVisible(isProfile);
                activityContent.setVisible(!isProfile);
                if (!isProfile && activityContent.getChildren().findFirst().isEmpty()) {
                    activityContent.add(buildActivityContent(user, auditUi));
                }
            });

            layoutContent = new Div(tabs, card, activityContent);
        } else {
            layoutContent = new Div(tabs, card);
        }

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

        layout.setContent(layoutContent);
        layout.setHeaderActions(new Div(editButton, closeButton));
    }

    private com.vaadin.flow.component.Component buildActivityContent(User user, AuditUiPort auditUi) {
        String currentName  = user.getName();
        String currentEmail = user.getEmail();
        org.ost.marketplace.entities.Role currentRole = user.getRole();
        UserSettings currentSettings = userSettingsService.load(user.getId());

        SnapshotBinder<UserSnapshotDto> userBinding = userBinderBuilder.build(
                SnapshotBinder.Parameters.<UserSnapshotDto>builder()
                        .entityType(org.ost.platform.core.model.EntityType.USER)
                        .snapshotClass(UserSnapshotDto.class)
                        .isCurrent(snap -> snap.name().equals(currentName)
                                        && snap.email() != null && snap.email().equals(currentEmail)
                                        && currentRole != null && currentRole.name().equals(snap.role()))
                        .subjectEntityId(user.getId())
                        .onRestore((snapshotId, entityId) -> params.getOnRestoreUser().accept(snapshotId, entityId))
                        .currentLabel(getValue(USER_ACTIVITY_CURRENT_STATE))
                        .restoreLabel(getValue(USER_RESTORE_BUTTON))
                        .build());

        SnapshotBinder<SettingsSnapshotDto> settingsBinding = settingsBinderBuilder.build(
                SnapshotBinder.Parameters.<SettingsSnapshotDto>builder()
                        .entityType(org.ost.platform.core.model.EntityType.USER_SETTINGS)
                        .snapshotClass(SettingsSnapshotDto.class)
                        .isCurrent(snap -> snap.adsPageSize() == currentSettings.getAdsPageSize()
                                        && snap.usersPageSize() == currentSettings.getUsersPageSize())
                        .subjectEntityId(user.getId())
                        .currentLabel(getValue(USER_ACTIVITY_CURRENT_STATE))
                        .restoreLabel(getValue(SETTINGS_RESTORE_BUTTON))
                        .build());

        return auditUi.buildProfileActivityPanel(AuditUiPort.ProfileActivityParams.builder()
                .subjectType(org.ost.platform.core.model.EntityType.USER)
                .subjectId(user.getId())
                .viewerActorId(user.getId())
                .emptyLabel(getValue(ACTIVITY_EMPTY))
                .bindings(java.util.List.of(userBinding, settingsBinding))
                .build());
    }

    private UiLabeledField field(I18nKey labelKey, String value) {
        return labeledFieldBuilder.build(
                UiLabeledField.Parameters.builder()
                        .labelKey(labelKey)
                        .value(value)
                        .build());
    }
}
