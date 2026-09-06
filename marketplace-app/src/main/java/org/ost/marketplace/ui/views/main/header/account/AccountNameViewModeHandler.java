package org.ost.marketplace.ui.views.main.header.account;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.orchestrator.services.UserProfileService;
import org.ost.platform.user.dto.UserDto;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.components.fields.UiLabeledField;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.marketplace.ui.views.components.overlay.AbstractViewOverlayModeHandler;
import org.ost.marketplace.ui.core.Configurable;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.ost.marketplace.ui.query.utils.TimeZoneUtil;
import org.springframework.context.annotation.Scope;

import static org.ost.marketplace.services.i18n.I18nKey.*;

/**
 * {@code AccountOverlay}'s Name section, View mode -- the default entry point (mirrors the
 * deleted {@code UserViewOverlayModeHandler}'s card layout). The Edit button is visible only for
 * self/admin ({@link AccessEvaluator#canEditUserAccount}); a moderator viewing another user's
 * account sees this card with no way to reach Edit mode at all (see {@code improvement-178}).
 */
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class AccountNameViewModeHandler extends AbstractViewOverlayModeHandler
        implements Configurable<AccountNameViewModeHandler, AccountNameViewModeHandler.Parameters>, I18nParams {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull Long targetUserId;
        @NonNull Runnable onEdit;
        @NonNull Runnable onClose;
        @NonNull Component tabBar;
    }

    private final UserProfileService userProfileService;
    private final AccessEvaluator    access;
    @Getter
    private final I18nService        i18nService;

    private Parameters params;

    @Override
    public AccountNameViewModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    @Override
    protected Div buildPrimaryContent() {
        UserDto user = userProfileService.findById(params.getTargetUserId()).orElseThrow();

        Div metaRow = new Div(
                field(USER_DIALOG_FIELD_ID_LABEL,      String.valueOf(user.id())),
                field(USER_DIALOG_FIELD_CREATED_LABEL,  TimeZoneUtil.formatInstantHuman(user.createdAt())),
                field(USER_DIALOG_FIELD_UPDATED_LABEL,  TimeZoneUtil.formatInstantHuman(user.updatedAt())));
        metaRow.addClassName("user-view-meta-row");

        Div cardHeader = new Div(VaadinIcon.USER.create(), new Span(getValue(USER_DIALOG_SECTION_VIEW)));
        cardHeader.addClassName("overlay__view-card-header");
        cardHeader.addClassName("overlay__view-card-header--" + user.role().name().toLowerCase());

        Div card = new Div(cardHeader, buildProfileRow(user), metaRow);
        card.addClassName("user-view-card");
        card.addClassName("user-view-card--" + user.role().name().toLowerCase());

        return new Div(params.getTabBar(), card);
    }

    private Div buildProfileRow(UserDto user) {
        String initials = user.name() != null && !user.name().isBlank()
                ? user.name().substring(0, Math.min(2, user.name().length())).toUpperCase()
                : "?";
        Div avatar = new Div(new Span(initials));
        avatar.addClassName("user-view-avatar");

        H2 nameHeading = new H2(user.name());
        nameHeading.addClassName("user-view-name");

        Span emailSpan = new Span(user.email());
        emailSpan.addClassName("user-view-email");

        Span roleBadge = new Span(user.role().name());
        roleBadge.addClassName("user-role-badge");
        roleBadge.addClassName("user-role-" + user.role().name().toLowerCase());

        Div nameBlock = new Div(nameHeading, emailSpan, roleBadge);
        nameBlock.addClassName("user-view-name-block");

        Div profileRow = new Div(avatar, nameBlock);
        profileRow.addClassName("user-view-profile-row");
        return profileRow;
    }

    @Override
    protected Div buildHeaderActions() {
        UiPrimaryButton editButton = new UiPrimaryButton(getValue(USER_VIEW_BUTTON_EDIT));
        UiIconButton closeButton = new UiIconButton(getValue(MAIN_TAB_USERS), VaadinIcon.CLOSE.create());
        editButton.addClickListener(_  -> params.getOnEdit().run());
        closeButton.addClickListener(_ -> params.getOnClose().run());
        editButton.setVisible(access.canEditUserAccount(params.getTargetUserId()));
        return new Div(editButton, closeButton);
    }

    private UiLabeledField field(I18nKey labelKey, String value) {
        return new UiLabeledField(getValue(labelKey), value);
    }
}
