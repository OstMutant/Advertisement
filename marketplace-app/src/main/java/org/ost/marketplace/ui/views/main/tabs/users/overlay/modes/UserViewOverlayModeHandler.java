package org.ost.marketplace.ui.views.main.tabs.users.overlay.modes;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.tabs.Tab;
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
import org.ost.marketplace.ui.views.components.overlay.AbstractViewOverlayModeHandler;
import org.ost.platform.ui.spi.audit.AuditUiPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.ui.Configurable;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.ost.platform.core.model.EntityType;
import org.ost.query.ui.utils.TimeZoneUtil;
import org.springframework.context.annotation.Scope;

import static org.ost.marketplace.common.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class UserViewOverlayModeHandler extends AbstractViewOverlayModeHandler
        implements Configurable<UserViewOverlayModeHandler, UserViewOverlayModeHandler.Parameters>,
                   I18nParams {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull User     user;
        @NonNull Runnable onEdit;
        @NonNull Runnable onClose;
    }

    private final AccessEvaluator                                   access;
    @Getter
    private final I18nService                                       i18nService;
    private final transient ComponentFactory<AuditUiPort>           auditUiPortFactory;
    private final transient ComponentFactory<UiPrimaryButton>       primaryButtonFactory;
    private final transient ComponentFactory<UiIconButton>          iconButtonFactory;
    private final transient ComponentFactory<UiLabeledField>        labeledFieldFactory;

    private Parameters params;

    @Override
    public UserViewOverlayModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    @Override
    protected String tabsCssClass() {
        return "user-view-tabs";
    }

    @Override
    protected Tab buildPrimaryTab() {
        return new Tab(getValue(ACTIVITY_PROFILE_TAB));
    }

    @Override
    protected Div buildPrimaryContent() {
        User user = params.getUser();

        Div metaRow = new Div(
                field(USER_DIALOG_FIELD_ID_LABEL,      String.valueOf(user.getId())),
                field(USER_DIALOG_FIELD_CREATED_LABEL,  TimeZoneUtil.formatInstantHuman(user.getCreatedAt())),
                field(USER_DIALOG_FIELD_UPDATED_LABEL,  TimeZoneUtil.formatInstantHuman(user.getUpdatedAt())));
        metaRow.addClassName("user-view-meta-row");

        Div cardHeader = new Div(VaadinIcon.USER.create(), new Span(getValue(USER_DIALOG_SECTION_VIEW)));
        cardHeader.addClassName("overlay__view-card-header");

        Div card = new Div(cardHeader, buildProfileRow(user), metaRow);
        card.addClassName("user-view-card");

        return card;
    }

    private Div buildProfileRow(User user) {
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
        return profileRow;
    }

    @Override
    protected SecondaryTabDef buildSecondaryTab() {
        return auditUiPortFactory.findIfAvailable()
                .map(auditUi -> new SecondaryTabDef(
                        new Tab(getValue(TIMELINE_TAB)),
                        "activity-feed-content",
                        () -> buildTimelineContent(params.getUser(), auditUi)))
                .orElse(null);
    }

    @Override
    protected Div buildHeaderActions() {
        UiPrimaryButton editButton = primaryButtonFactory.build(
                UiPrimaryButton.Parameters.builder().labelKey(USER_VIEW_BUTTON_EDIT).build());
        UiIconButton closeButton = iconButtonFactory.build(
                UiIconButton.Parameters.builder()
                        .labelKey(MAIN_TAB_USERS)
                        .icon(VaadinIcon.CLOSE.create())
                        .build());
        editButton.addClickListener(_  -> params.getOnEdit().run());
        closeButton.addClickListener(_ -> params.getOnClose().run());
        editButton.setVisible(access.canOperate(params.getUser()));
        return new Div(editButton, closeButton);
    }

    private com.vaadin.flow.component.Component buildTimelineContent(User user, AuditUiPort auditUi) {
        return auditUi.buildAuditTimelinePanel(AuditUiPort.TimelineParams.builder()
                .actorId(user.getId())
                .viewerActorId(user.getId())
                .build());
    }

    private UiLabeledField field(I18nKey labelKey, String value) {
        return labeledFieldFactory.build(
                UiLabeledField.Parameters.builder()
                        .labelKey(labelKey)
                        .value(value)
                        .build());
    }
}
