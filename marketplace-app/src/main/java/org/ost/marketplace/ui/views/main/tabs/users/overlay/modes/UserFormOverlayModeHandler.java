package org.ost.marketplace.ui.views.main.tabs.users.overlay.modes;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.marketplace.entities.Role;
import org.ost.marketplace.entities.User;
import org.ost.platform.core.i18n.I18nService;
import org.ost.marketplace.services.user.UserService;
import org.ost.marketplace.ui.dto.UserEditDto;
import org.ost.marketplace.ui.mappers.UserMapper;
import org.ost.marketplace.ui.views.components.overlay.AbstractFormOverlayModeHandler;
import org.ost.marketplace.ui.views.components.overlay.OverlayFormBinder;
import org.ost.marketplace.ui.views.components.fields.UiComboBox;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.marketplace.ui.views.components.buttons.UiTertiaryButton;
import org.ost.marketplace.ui.views.components.fields.UiTextField;
import org.ost.marketplace.ui.views.components.overlay.OverlayLayout;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.ui.Configurable;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.springframework.context.annotation.Scope;

import java.util.Arrays;

import static org.ost.marketplace.common.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class UserFormOverlayModeHandler extends AbstractFormOverlayModeHandler<UserEditDto>
        implements Configurable<UserFormOverlayModeHandler, UserFormOverlayModeHandler.Parameters>, I18nParams {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull User     user;
        @NonNull Runnable onSave;
        @NonNull Runnable onCancel;
    }

    private final UserService                              userService;
    private final UserMapper                               mapper;
    @Getter
    private final I18nService                              i18nService;
    private final transient ComponentFactory<OverlayFormBinder<UserEditDto>> formBinderFactory;
    private final UiTextField                              nameField;
    private final UiComboBox<Role>                         roleComboBox;
    private final UiPrimaryButton                          saveButton;
    private final UiTertiaryButton                         cancelButton;

    private Parameters params;

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
        cancelButton.configure(UiTertiaryButton.Parameters.builder()
                .labelKey(USER_DIALOG_BUTTON_CANCEL).build());

        wireSaveGuard(saveButton, params.getOnSave());
        cancelButton.addClickListener(_ -> params.getOnCancel().run());

        UserEditDto dto = mapper.toUserEdit(params.getUser());
        buildBinder(dto);

        Div cardHeader = new Div(VaadinIcon.USER.create(), new Span(getValue(USER_DIALOG_SECTION_LABEL)));
        cardHeader.addClassName("overlay__form-card-header");

        Div fieldsCard = new Div(cardHeader, nameField, roleComboBox);
        fieldsCard.addClassName("overlay__form-fields-card");

        layout.setContent(new Div(fieldsCard));
        layout.setHeaderActions(new Div(saveButton, cancelButton));
    }

    public Long getSavedUserId() {
        return params.getUser().getId();
    }

    public boolean save() {
        return binder.save(dto -> userService.save(mapper.copy(dto)));
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
