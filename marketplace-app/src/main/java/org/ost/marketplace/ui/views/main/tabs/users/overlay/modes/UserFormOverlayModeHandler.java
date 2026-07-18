package org.ost.marketplace.ui.views.main.tabs.users.overlay.modes;

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
import org.ost.platform.audit.dto.AuditSnapshotContentDto;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.dto.UserSnapshotDto;
import org.ost.platform.user.model.Role;
import org.ost.platform.user.spi.UserPort;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.marketplace.ui.views.components.audit.AuditActivityPanel;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.dto.UserEditDto;
import org.ost.marketplace.ui.mappers.UserMapper;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.components.overlay.AbstractFormOverlayModeHandler;
import org.ost.marketplace.ui.views.components.overlay.OverlayFormBinder;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.marketplace.ui.views.components.fields.UiComboBox;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.marketplace.ui.views.components.buttons.UiTertiaryButton;
import org.ost.marketplace.ui.views.components.fields.UiTextField;
import org.ost.marketplace.ui.views.components.overlay.OverlayLayout;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.marketplace.ui.core.Configurable;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.springframework.context.annotation.Scope;

import java.util.Arrays;

import static org.ost.marketplace.services.i18n.I18nKey.*;
import static org.ost.marketplace.services.i18n.I18nKey.FORM_DISCARD_CHANGES;
import static org.ost.marketplace.services.i18n.I18nKey.FORM_RESTORE_BANNER;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class UserFormOverlayModeHandler extends AbstractFormOverlayModeHandler<UserEditDto>
        implements Configurable<UserFormOverlayModeHandler, UserFormOverlayModeHandler.Parameters>, I18nParams {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull UserDto  user;
        @NonNull Runnable onSave;
        @NonNull Runnable onCancel;
    }

    private final UserPort                                              userPort;
    private final UserMapper                                            mapper;
    private final AccessEvaluator                                       access;
    @Getter
    private final I18nService                                           i18nService;
    private final NotificationService                                   notificationService;
    private final UiComponentFactory<OverlayFormBinder<UserEditDto>> formBinderFactory;
    private final ComponentFactory<AuditPort>                        auditPortFactory;
    private final UiComponentFactory<AuditActivityPanel>             auditActivityPanelFactory;
    private final UiComponentFactory<UiIconButton>                   cancelButtonFactory;
    private final UiTextField                                           nameField;
    private final UiComboBox<Role>                                      roleComboBox;
    private final UiPrimaryButton                                       saveButton;
    private final UiTertiaryButton                                      discardButton;

    private Parameters params;
    @Getter
    private UserDto    savedUser;

    @Override
    public UserFormOverlayModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    @Override
    public void activate(OverlayLayout layout) {
        nameField.configure(UiTextField.Parameters.builder()
                .labelKey(USER_DIALOG_FIELD_NAME_LABEL)
                .placeholderKey(USER_DIALOG_FIELD_NAME_PLACEHOLDER)
                .maxLength(255)
                .required(true)
                .build());

        roleComboBox.configure(UiComboBox.Parameters.<Role>builder()
                .labelKey(USER_DIALOG_FIELD_ROLE_LABEL)
                .items(Arrays.asList(Role.values()))
                .required(true)
                .build());

        saveButton.configure(UiPrimaryButton.Parameters.builder()
                .labelKey(USER_DIALOG_BUTTON_SAVE).build());
        discardButton.configure(UiTertiaryButton.Parameters.builder()
                .labelKey(FORM_DISCARD_CHANGES).build());
        UiIconButton closeBtn = cancelButtonFactory.build(UiIconButton.Parameters.builder()
                .labelKey(USER_DIALOG_BUTTON_CANCEL)
                .icon(VaadinIcon.CLOSE.create())
                .build());

        wireSaveGuard(saveButton, params.getOnSave());
        discardButton.addClickListener(_ -> discardChanges());
        closeBtn.addClickListener(_ -> params.getOnCancel().run());

        UserEditDto dto = mapper.toUserEdit(params.getUser());
        buildBinder(dto);
        nameField.setValueChangeMode(ValueChangeMode.EAGER);
        nameField.addValueChangeListener(_ -> updateButtons(binder.hasChanges()));
        roleComboBox.addValueChangeListener(_ -> updateButtons(binder.hasChanges()));

        Div cardHeader = new Div(VaadinIcon.USER.create(), new Span(getValue(USER_DIALOG_SECTION_LABEL)));
        cardHeader.addClassName("overlay__form-card-header");

        Div fieldsCard = new Div(cardHeader, nameField, roleComboBox);
        fieldsCard.addClassName("overlay__form-fields-card");

        Div editContent = new Div(fieldsCard);

        Div content = buildContentWithActivity(ActivityTabParams.builder()
                .canOperate(access.canOperate(params.getUser().id()))
                .isCreateMode(false)
                .editTabLabel(getValue(USER_DIALOG_SECTION_LABEL))
                .activityTabLabel(getValue(USER_ACTIVITY_TAB))
                .tabsCssClass("user-form-tabs")
                .secondaryContentCssClass("activity-feed-content")
                .editContent(editContent)
                .auditPortFactory(auditPortFactory)
                .activityContentLoader(this::buildActivityContent)
                .build());

        layout.setContent(content);
        layout.setHeaderActions(new Div(saveButton, discardButton, closeBtn));
        updateButtons(false);
    }

    public Long getSavedUserId() {
        return params.getUser().id();
    }

    public boolean save() {
        return binder.save(dto -> {
            userPort.save(mapper.copy(dto), access.getCurrentUserId());
            userPort.findById(params.getUser().id()).ifPresent(u -> {
                savedUser = u;
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
        if (formTabs != null) formTabs.setSelectedTab(editTab);
    }

    private com.vaadin.flow.component.Component buildActivityContent() {
        return auditActivityPanelFactory.build(AuditActivityPanel.Parameters.builder()
                .entityRef(new EntityRef(EntityType.USER, params.getUser().id()))
                .userId(access.getCurrentUserId())
                .isPrivileged(access.isPrivileged())
                .canOperate(access.canOperate(params.getUser().id()))
                .onRestoreRequested(this::handleRestoreFromActivity)
                .build());
    }

    private void handleRestoreFromActivity(Long snapshotId) {
        auditPortFactory.ifAvailable(port ->
                port.<UserSnapshotDto>getSnapshotContent(snapshotId, EntityType.USER)
                        .map(AuditSnapshotContentDto::snapshotData)
                        .ifPresent(snapshot -> {
                            UserEditDto dto = new UserEditDto(params.getUser().id(), snapshot.name(), Role.valueOf(snapshot.role()), params.getUser().version());
                            loadRestored(dto);
                        })
        );
    }

    public void discardChanges() {
        userPort.findById(params.getUser().id()).ifPresent(freshUser -> {
            UserEditDto fresh = mapper.toUserEdit(freshUser);
            binder.reload(fresh, (src, tgt) -> {
                tgt.setName(src.getName());
                tgt.setRole(src.getRole());
            });
            updateButtons(false);
        });
    }

    public void afterSave(boolean success) {
        if (success) {
            updateButtons(false);
            if (formTabs != null) formTabs.setSelectedTab(editTab);
            if (tabbedSecondaryContent != null) tabbedSecondaryContent.removeAll();
        } else {
            updateButtons(true);
        }
    }

    private void updateButtons(boolean hasChanges) {
        saveButton.setEnabled(hasChanges);
        discardButton.setEnabled(hasChanges);
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
