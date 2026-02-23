package org.ost.advertisement.ui.views.advertisements.overlay;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.security.AccessEvaluator;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.dto.AdvertisementEditDto;
import org.ost.advertisement.ui.mappers.AdvertisementMapper;
import org.ost.advertisement.ui.services.NotificationService;
import org.ost.advertisement.ui.views.advertisements.overlay.fields.OverlayAdvertisementBreadcrumbButton;
import org.ost.advertisement.ui.views.advertisements.overlay.fields.OverlayAdvertisementCancelButton;
import org.ost.advertisement.ui.views.advertisements.overlay.fields.OverlayAdvertisementCloseButton;
import org.ost.advertisement.ui.views.advertisements.overlay.fields.OverlayAdvertisementDescriptionTextArea;
import org.ost.advertisement.ui.views.advertisements.overlay.fields.OverlayAdvertisementEditButton;
import org.ost.advertisement.ui.views.advertisements.overlay.fields.OverlayAdvertisementMetaPanel;
import org.ost.advertisement.ui.views.advertisements.overlay.fields.OverlayAdvertisementSaveButton;
import org.ost.advertisement.ui.views.advertisements.overlay.fields.OverlayAdvertisementTitleTextField;
import org.ost.advertisement.ui.views.components.dialogs.FormDialogBinder;
import org.ost.advertisement.ui.views.components.overlay.BaseOverlay;
import org.ost.advertisement.ui.views.components.overlay.OverlayLayout;

import static org.ost.advertisement.constants.I18nKey.*;

/**
 * Full-viewport overlay (position:fixed). Pure Divs — no Vaadin layout components.
 * Header actions per mode:
 *   VIEW   — [Edit]  [Close]
 *   EDIT   — [Save]  [Cancel]
 *   CREATE — [Save]  [Cancel]
 * Cancel / ESC behavior depends on how EDIT was entered:
 *   - card Edit button    → cancel closes overlay (returns to card list)
 *   - Edit button in VIEW → cancel returns to VIEW mode
 * On save: refresh callback runs first, then overlay closes.
 */
@SpringComponent
@UIScope
@RequiredArgsConstructor
public class AdvertisementOverlay extends BaseOverlay {

    private enum Mode { VIEW, EDIT, CREATE }

    private final transient AdvertisementService                           advertisementService;
    private final transient AdvertisementMapper                            mapper;
    private final transient I18nService                                    i18n;
    private final transient NotificationService                            notification;
    private final transient AccessEvaluator                                access;
    private final transient OverlayAdvertisementMetaPanel.Builder          metaPanelBuilder;
    private final transient FormDialogBinder.Builder<AdvertisementEditDto> binderBuilder;

    // injected field components — labels/placeholders/validation configured in their constructors
    private final OverlayAdvertisementTitleTextField      titleField;
    private final OverlayAdvertisementDescriptionTextArea descriptionField;
    private final OverlayAdvertisementBreadcrumbButton    breadcrumbButton;
    private final OverlayAdvertisementEditButton          editButton;
    private final OverlayAdvertisementCloseButton         closeButton;
    private final OverlayAdvertisementSaveButton          saveButton;
    private final OverlayAdvertisementCancelButton        cancelButton;

    @Getter
    private final OverlayLayout layout;

    private Mode                 currentMode;
    private AdvertisementInfoDto currentAd;
    private Runnable             onSavedCallback;

    // true when EDIT was entered via the Edit button inside VIEW mode
    private boolean enteredEditFromView = false;

    private H2   viewTitle;
    private Span viewDescription;

    private FormDialogBinder<AdvertisementEditDto> formBinder;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    // onChanged is stored so that editing from VIEW mode also triggers a refresh on save
    public void openForView(AdvertisementInfoDto ad, Runnable onChanged) {
        ensureInitialized();
        currentAd           = ad;
        currentMode         = Mode.VIEW;
        onSavedCallback     = onChanged;
        enteredEditFromView = false;
        populateView(ad);
        rebuildMeta(ad);
        switchTo(Mode.VIEW);
        open();
    }

    public void openForCreate(Runnable onSaved) {
        ensureInitialized();
        currentAd           = null;
        currentMode         = Mode.CREATE;
        onSavedCallback     = onSaved;
        enteredEditFromView = false;
        rebuildFormBinder(new AdvertisementEditDto());
        switchTo(Mode.CREATE);
        open();
    }

    // Opened directly from the card Edit button — cancel should close overlay entirely
    public void openForEdit(AdvertisementInfoDto ad, Runnable onSaved) {
        ensureInitialized();
        currentAd           = ad;
        currentMode         = Mode.EDIT;
        onSavedCallback     = onSaved;
        enteredEditFromView = false;
        rebuildMeta(ad);
        rebuildFormBinder(mapper.toAdvertisementEdit(ad));
        switchTo(Mode.EDIT);
        open();
    }

    // -------------------------------------------------------------------------
    // BaseOverlay contract
    // -------------------------------------------------------------------------

    @Override
    protected void buildContent(OverlayLayout l) {
        addClassName("advertisement-overlay");

        breadcrumbButton.addClickListener(_ -> closeToList());
        l.setBreadcrumbButton(breadcrumbButton);

        editButton.addClickListener(_   -> switchToEdit());
        closeButton.addClickListener(_  -> closeToList());
        saveButton.addClickListener(_   -> handleSave());
        cancelButton.addClickListener(_ -> handleCancel());
        l.addHeaderActions(editButton, closeButton, saveButton, cancelButton);

        viewTitle       = new H2();   viewTitle.addClassName("overlay__view-title");
        viewDescription = new Span(); viewDescription.addClassName("overlay__view-description");
        l.addViewContent(viewTitle, viewDescription);

        titleField.setWidthFull();
        descriptionField.setWidthFull();
        l.addEditContent(titleField, descriptionField);
    }

    @Override
    protected void onEsc() {
        handleCancel();
    }

    // -------------------------------------------------------------------------
    // Mode switching
    // -------------------------------------------------------------------------

    private void switchTo(Mode mode) {
        boolean isView   = mode == Mode.VIEW;
        boolean isEdit   = mode == Mode.EDIT;
        boolean isCreate = mode == Mode.CREATE;

        layout.getViewBody().setVisible(isView);
        layout.getEditBody().setVisible(isEdit || isCreate);
        layout.getMetaContainer().setVisible(isView || isEdit);

        editButton.setVisible(isView && currentAd != null && access.canOperate(currentAd));
        closeButton.setVisible(isView);
        saveButton.setVisible(isEdit || isCreate);
        cancelButton.setVisible(isEdit || isCreate);

        layout.getBreadcrumbCurrent().setText(switch (mode) {
            case VIEW   -> "";
            case EDIT   -> i18n.get(ADVERTISEMENT_OVERLAY_TITLE_EDIT);
            case CREATE -> i18n.get(ADVERTISEMENT_OVERLAY_TITLE_NEW);
        });
        layout.getBreadcrumbCurrent().setVisible(mode != Mode.VIEW);
    }

    // Entered EDIT from within VIEW — cancel must return to VIEW, not close
    private void switchToEdit() {
        if (currentAd == null) return;
        currentMode         = Mode.EDIT;
        enteredEditFromView = true;
        rebuildFormBinder(mapper.toAdvertisementEdit(currentAd));
        switchTo(Mode.EDIT);
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    private void populateView(AdvertisementInfoDto ad) {
        viewTitle.setText(ad.getTitle());
        viewDescription.setText(ad.getDescription());
    }

    private void rebuildMeta(AdvertisementInfoDto ad) {
        layout.getMetaContainer().removeAll();
        layout.getMetaContainer().add(metaPanelBuilder.build(
                OverlayAdvertisementMetaPanel.Parameters.builder()
                        .authorName(ad.getCreatedByUserName() != null ? ad.getCreatedByUserName() : "—")
                        .createdAt(ad.getCreatedAt())
                        .updatedAt(ad.getUpdatedAt())
                        .build()
        ));
    }

    private void rebuildFormBinder(AdvertisementEditDto dto) {
        formBinder = binderBuilder.build(
                FormDialogBinder.Config.<AdvertisementEditDto>builder()
                        .clazz(AdvertisementEditDto.class)
                        .dto(dto)
                        .build()
        );
        bindFields();
    }

    private void bindFields() {
        formBinder.getBinder().forField(titleField)
                .asRequired(i18n.get(ADVERTISEMENT_OVERLAY_VALIDATION_TITLE_REQUIRED))
                .withValidator(new StringLengthValidator(
                        i18n.get(ADVERTISEMENT_OVERLAY_VALIDATION_TITLE_LENGTH), 1, 255))
                .bind(AdvertisementEditDto::getTitle, AdvertisementEditDto::setTitle);

        formBinder.getBinder().forField(descriptionField)
                .asRequired(i18n.get(ADVERTISEMENT_OVERLAY_VALIDATION_DESCRIPTION_REQUIRED))
                .bind(AdvertisementEditDto::getDescription, AdvertisementEditDto::setDescription);
    }

    private void handleSave() {
        boolean saved = formBinder.save(dto ->
                advertisementService.save(mapper.toAdvertisement(dto))
        );
        if (saved) {
            notification.success(ADVERTISEMENT_OVERLAY_NOTIFICATION_SUCCESS);
            if (onSavedCallback != null) onSavedCallback.run();
            closeToList();
        } else {
            notification.error(ADVERTISEMENT_OVERLAY_NOTIFICATION_VALIDATION_FAILED);
        }
    }

    private void handleCancel() {
        if (currentMode == Mode.EDIT && enteredEditFromView) {
            currentMode         = Mode.VIEW;
            enteredEditFromView = false;
            switchTo(Mode.VIEW);
            populateView(currentAd);
        } else {
            closeToList();
        }
    }
}