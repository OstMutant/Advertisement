package org.ost.marketplace.ui.views.main.header.account;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.orchestrator.services.AuditQueryService;
import org.ost.orchestrator.services.UserProfileService;
import org.ost.platform.audit.dto.AuditSnapshotContentDto;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.dto.UserSnapshotDto;
import org.ost.platform.user.model.Role;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.views.components.audit.EntityActivityOverlay;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.dto.UserEditDto;
import org.ost.marketplace.ui.mappers.UserMapper;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.components.overlay.AbstractFormOverlayModeHandler;
import org.ost.marketplace.ui.views.components.overlay.BreadcrumbStep;
import org.ost.marketplace.ui.views.components.overlay.OverlayFormBinder;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.marketplace.ui.views.utils.BeforeUnloadUtil;
import org.ost.marketplace.ui.views.components.fields.UiComboBox;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.marketplace.ui.views.components.buttons.UiTertiaryButton;
import org.ost.marketplace.ui.views.components.fields.UiTextField;
import org.ost.marketplace.ui.views.components.overlay.OverlayLayout;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.marketplace.ui.core.Configurable;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.springframework.context.annotation.Scope;

import java.util.Arrays;
import java.util.List;

import static org.ost.marketplace.services.i18n.I18nKey.*;

/**
 * {@code AccountOverlay}'s Name section, Edit mode -- reached only via
 * {@link AccountNameViewModeHandler}'s Edit button, itself only visible when
 * {@link AccessEvaluator#canEditUserAccount} is true, so this handler can assume edit rights on
 * the name itself. The role field is gated separately and more narrowly by
 * {@link AccessEvaluator#canEditRole}, never by self, even an admin's own role
 * (see {@code improvement-178}).
 */
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class AccountNameFormModeHandler extends AbstractFormOverlayModeHandler<UserEditDto>
        implements Configurable<AccountNameFormModeHandler, AccountNameFormModeHandler.Parameters>, I18nParams {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull Long targetUserId;
        @NonNull Runnable onSave;
        @NonNull Runnable onCancel;
        @NonNull List<BreadcrumbStep> breadcrumbSteps;
        @NonNull Component tabBar;
    }

    private final UserProfileService                                 userProfileService;
    private final UserMapper                                         mapper;
    private final AccessEvaluator                                    access;
    @Getter
    private final I18nService                                        i18nService;
    private final NotificationService                                notificationService;
    private final UiComponentFactory<OverlayFormBinder<UserEditDto>> formBinderFactory;
    private final AuditQueryService                                  auditQueryService;
    private final EntityActivityOverlay                              entityActivityOverlay;

    private Parameters params;
    private UserDto    currentUser;
    private UiPrimaryButton  saveButton;
    private UiTertiaryButton discardButton;
    private UiTextField      nameField;
    private UiComboBox<Role> roleComboBox;

    @Override
    public AccountNameFormModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    public UserDto getSavedUser() {
        return currentUser;
    }

    @Override
    public void activate(OverlayLayout layout) {
        currentUser = userProfileService.findById(params.getTargetUserId()).orElseThrow();
        // openForEdit() reaches this handler directly, bypassing the View mode's own Edit-button gate.
        boolean canEdit = access.canEditUserAccount(params.getTargetUserId());
        boolean canEditRole = access.canEditRole(params.getTargetUserId());

        nameField = new UiTextField(getValue(USER_DIALOG_FIELD_NAME_LABEL), getValue(USER_DIALOG_FIELD_NAME_PLACEHOLDER),
                255, true, USER_DIALOG_FIELD_NAME_LABEL.toTestId());
        nameField.setReadOnly(!canEdit);

        roleComboBox = new UiComboBox<>(getValue(USER_DIALOG_FIELD_ROLE_LABEL), Arrays.asList(Role.values()),
                true, USER_DIALOG_FIELD_ROLE_LABEL.toTestId());
        roleComboBox.setReadOnly(!canEditRole);

        saveButton = new UiPrimaryButton(getValue(USER_DIALOG_BUTTON_SAVE));
        saveButton.setVisible(canEdit);
        discardButton = new UiTertiaryButton(getValue(FORM_DISCARD_CHANGES));
        discardButton.setVisible(canEdit);
        UiIconButton closeBtn = new UiIconButton(getValue(USER_DIALOG_BUTTON_CANCEL), VaadinIcon.CLOSE.create());

        wireSaveGuard(saveButton, params.getOnSave());
        discardButton.addClickListener(_ -> discardChanges());
        closeBtn.addClickListener(_ -> params.getOnCancel().run());

        UserEditDto dto = mapper.toUserEdit(currentUser);
        buildBinder(dto);
        nameField.setValueChangeMode(ValueChangeMode.EAGER);
        nameField.addValueChangeListener(_ -> updateButtons(binder.hasChanges()));
        roleComboBox.addValueChangeListener(_ -> updateButtons(binder.hasChanges()));

        Div cardHeader = new Div(VaadinIcon.USER.create(), new Span(getValue(USER_DIALOG_SECTION_LABEL)));
        cardHeader.addClassName("overlay__form-card-header");

        Div fieldsCard = new Div(cardHeader, nameField, roleComboBox);
        fieldsCard.addClassName("overlay__form-fields-card");

        Div editContent = new Div(params.getTabBar(), fieldsCard);
        layout.setContent(editContent);

        Div headerActions = new Div(saveButton, discardButton);
        if (auditQueryService.isAvailable()) {
            headerActions.add(buildHistoryButton());
        }
        headerActions.add(closeBtn);
        layout.setHeaderActions(headerActions);
        updateButtons(false);
    }

    private UiIconButton buildHistoryButton() {
        UiIconButton historyBtn = new UiIconButton(getValue(USER_ACTIVITY_BUTTON), VaadinIcon.CLOCK.create());
        historyBtn.addClassName("user-history-button");
        historyBtn.addClickListener(_ -> entityActivityOverlay.openFor(EntityActivityOverlay.Parameters.builder()
                .entityRef(new EntityRef(EntityType.USER, params.getTargetUserId()))
                .userId(access.getCurrentUserId())
                .isPrivileged(access.isPrivileged())
                .canOperate(true)
                .parentSteps(params.getBreadcrumbSteps())
                .parentFormLabel(currentUser.name())
                .currentLabelKey(USER_ACTIVITY_BUTTON)
                .onRestoreRequested(this::handleRestoreFromActivity)
                .build()));
        return historyBtn;
    }

    @Override
    public boolean save() {
        return binder.save(dto -> {
            userProfileService.save(mapper.copy(dto), access.getCurrentUserId());
            userProfileService.findById(params.getTargetUserId()).ifPresent(u -> {
                currentUser = u;
                dto.setVersion(u.version());
            });
        });
    }

    public void loadRestored(@NonNull UserEditDto restoredDto) {
        binder.loadRestored(restoredDto, (src, tgt) -> {
            tgt.setName(src.getName());
            tgt.setRole(src.getRole());
        });
        notificationService.success(FORM_RESTORE_BANNER);
        updateButtons(true);
    }

    private void handleRestoreFromActivity(Long snapshotId) {
        auditQueryService.getSnapshotContent(snapshotId, EntityType.USER, UserSnapshotDto.class)
                .map(AuditSnapshotContentDto::snapshotData)
                .ifPresent(snapshot -> {
                    UserEditDto dto = new UserEditDto(params.getTargetUserId(), snapshot.name(),
                            Role.valueOf(snapshot.role()), currentUser.version());
                    loadRestored(dto);
                });
    }

    @Override
    public void discardChanges() {
        userProfileService.findById(params.getTargetUserId()).ifPresent(freshUser -> {
            UserEditDto fresh = mapper.toUserEdit(freshUser);
            binder.reload(fresh, (src, tgt) -> {
                tgt.setName(src.getName());
                tgt.setRole(src.getRole());
            });
            updateButtons(false);
        });
    }

    @Override
    public void afterSave(boolean success) {
        updateButtons(!success);
    }

    private void updateButtons(boolean hasChanges) {
        saveButton.setEnabled(hasChanges);
        discardButton.setEnabled(hasChanges);
        BeforeUnloadUtil.sync(hasChanges);
    }

    private void buildBinder(UserEditDto dto) {
        binder = formBinderFactory.build(
                OverlayFormBinder.Parameters.<UserEditDto>builder()
                        .clazz(UserEditDto.class)
                        .dto(dto)
                        .build()
        );
        binder.getBinder().forField(nameField)
                .asRequired(getValue(USER_DIALOG_VALIDATION_NAME_REQUIRED))
                .withValidator(new StringLengthValidator(
                        getValue(USER_DIALOG_VALIDATION_NAME_LENGTH), 1, 255))
                .bind(UserEditDto::getName, UserEditDto::setName);
        binder.getBinder().forField(roleComboBox)
                .asRequired(getValue(USER_DIALOG_VALIDATION_ROLE_REQUIRED))
                .bind(UserEditDto::getRole, UserEditDto::setRole);
        binder.readInitialValues();
    }
}
