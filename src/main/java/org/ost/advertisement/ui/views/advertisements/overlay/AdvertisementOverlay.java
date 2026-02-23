package org.ost.advertisement.ui.views.advertisements.overlay;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.ShortcutRegistration;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.security.AccessEvaluator;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.dto.AdvertisementEditDto;
import org.ost.advertisement.ui.mappers.AdvertisementMapper;
import org.ost.advertisement.ui.services.NotificationService;
import org.ost.advertisement.ui.views.advertisements.overlay.fields.OverlayAdvertisementCancelButton;
import org.ost.advertisement.ui.views.advertisements.overlay.fields.OverlayAdvertisementDescriptionTextArea;
import org.ost.advertisement.ui.views.advertisements.overlay.fields.OverlayAdvertisementMetaPanel;
import org.ost.advertisement.ui.views.advertisements.overlay.fields.OverlayAdvertisementSaveButton;
import org.ost.advertisement.ui.views.advertisements.overlay.fields.OverlayAdvertisementTitleTextField;

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
public class AdvertisementOverlay extends Div {

    private enum Mode { VIEW, EDIT, CREATE }

    private final transient AdvertisementService                  advertisementService;
    private final transient AdvertisementMapper                   mapper;
    private final transient I18nService                           i18n;
    private final transient NotificationService                   notification;
    private final transient AccessEvaluator                       access;
    private final transient OverlayAdvertisementMetaPanel.Builder metaPanelBuilder;

    // injected field components — labels/placeholders/validation configured in their constructors
    private final OverlayAdvertisementTitleTextField      titleField;
    private final OverlayAdvertisementDescriptionTextArea descriptionField;
    private final OverlayAdvertisementSaveButton          saveButton;
    private final OverlayAdvertisementCancelButton        cancelButton;

    private Mode                 currentMode;
    private AdvertisementInfoDto currentAd;
    private Runnable             onSavedCallback;
    private ShortcutRegistration escShortcut;

    // true when EDIT was entered via the Edit button inside VIEW mode
    private boolean enteredEditFromView = false;
    private boolean initialized         = false;

    private Button breadcrumbBack;
    private Span   breadcrumbCurrent;
    private Button editButton;
    private Button closeButton;

    private H2   viewTitle;
    private Span viewDescription;
    private Div  viewBody;

    // container is fixed in the DOM; its child is replaced on each open
    private Div metaContainer;
    private Div editBody;

    private Binder<AdvertisementEditDto> binder;

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
        binder.setBean(new AdvertisementEditDto());
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
        binder.setBean(mapper.toAdvertisementEdit(ad));
        switchTo(Mode.EDIT);
        open();
    }

    // -------------------------------------------------------------------------
    // Layout
    // -------------------------------------------------------------------------

    private void ensureInitialized() {
        if (initialized) return;
        initialized = true;
        buildLayout();
        buildBinder();
    }

    private void buildLayout() {
        addClassName("advertisement-overlay");

        // -- breadcrumb -------------------------------------------------------
        breadcrumbBack = new Button(i18n.get(MAIN_TAB_ADVERTISEMENTS), _ -> closeToList());
        breadcrumbBack.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        breadcrumbBack.addClassName("overlay__breadcrumb-back");

        Span breadcrumbSep = new Span("›");
        breadcrumbSep.addClassName("overlay__breadcrumb-sep");

        breadcrumbCurrent = new Span();
        breadcrumbCurrent.addClassName("overlay__breadcrumb-current");

        Div breadcrumb = new Div(breadcrumbBack, breadcrumbSep, breadcrumbCurrent);
        breadcrumb.addClassName("overlay__breadcrumb");

        // -- header actions ---------------------------------------------------
        editButton  = new Button(i18n.get(ADVERTISEMENT_CARD_BUTTON_EDIT), _ -> switchToEdit());
        closeButton = new Button(VaadinIcon.CLOSE.create(),                  _ -> closeToList());

        editButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        closeButton.getElement().setAttribute("title", i18n.get(MAIN_TAB_ADVERTISEMENTS));

        saveButton.addClickListener(_   -> handleSave());
        cancelButton.addClickListener(_ -> handleCancel());

        Div headerActions = new Div(editButton, closeButton, saveButton, cancelButton);
        headerActions.addClassName("overlay__header-actions");

        Div header = new Div(breadcrumb, headerActions);
        header.addClassName("overlay__header");

        // -- view body --------------------------------------------------------
        viewTitle       = new H2();   viewTitle.addClassName("overlay__view-title");
        viewDescription = new Span(); viewDescription.addClassName("overlay__view-description");

        viewBody = new Div(viewTitle, viewDescription);
        viewBody.addClassName("overlay__view-body");

        // -- meta container (VIEW + EDIT, hidden in CREATE) -------------------
        metaContainer = new Div();
        metaContainer.addClassName("overlay__meta-container");

        // -- edit/create body -------------------------------------------------
        titleField.setWidthFull();
        descriptionField.setWidthFull();

        editBody = new Div(titleField, descriptionField);
        editBody.addClassName("overlay__edit-body");

        Div content = new Div(viewBody, editBody, metaContainer);
        content.addClassName("overlay__content");

        Div inner = new Div(header, content);
        inner.addClassName("overlay__inner");

        add(inner);
    }

    private void buildBinder() {
        binder = new Binder<>(AdvertisementEditDto.class);

        binder.forField(titleField)
                .asRequired(i18n.get(ADVERTISEMENT_OVERLAY_VALIDATION_TITLE_REQUIRED))
                .withValidator(new StringLengthValidator(
                        i18n.get(ADVERTISEMENT_OVERLAY_VALIDATION_TITLE_LENGTH), 1, 255))
                .bind(AdvertisementEditDto::getTitle, AdvertisementEditDto::setTitle);

        binder.forField(descriptionField)
                .asRequired(i18n.get(ADVERTISEMENT_OVERLAY_VALIDATION_DESCRIPTION_REQUIRED))
                .bind(AdvertisementEditDto::getDescription, AdvertisementEditDto::setDescription);
    }

    // -------------------------------------------------------------------------
    // Mode switching
    // -------------------------------------------------------------------------

    private void switchTo(Mode mode) {
        boolean isView   = mode == Mode.VIEW;
        boolean isEdit   = mode == Mode.EDIT;
        boolean isCreate = mode == Mode.CREATE;

        viewBody.setVisible(isView);
        editBody.setVisible(isEdit || isCreate);
        metaContainer.setVisible(isView || isEdit);

        editButton.setVisible(isView && currentAd != null && access.canOperate(currentAd));
        closeButton.setVisible(isView);
        saveButton.setVisible(isEdit || isCreate);
        cancelButton.setVisible(isEdit || isCreate);

        breadcrumbCurrent.setText(switch (mode) {
            case VIEW   -> "";
            case EDIT   -> i18n.get(ADVERTISEMENT_OVERLAY_TITLE_EDIT);
            case CREATE -> i18n.get(ADVERTISEMENT_OVERLAY_TITLE_NEW);
        });
        breadcrumbCurrent.setVisible(mode != Mode.VIEW);
    }

    // Entered EDIT from within VIEW — cancel must return to VIEW, not close
    private void switchToEdit() {
        if (currentAd == null) return;
        currentMode         = Mode.EDIT;
        enteredEditFromView = true;
        binder.setBean(mapper.toAdvertisementEdit(currentAd));
        switchTo(Mode.EDIT);
    }

    // -------------------------------------------------------------------------
    // Open / close
    // -------------------------------------------------------------------------

    private void open() {
        UI.getCurrent().getPage().executeJs(
                "var el = $0;" +
                        "var y  = Math.round(window.scrollY);" +
                        "el.dataset.savedScroll       = y;" +
                        "document.body.style.position = 'fixed';" +
                        "document.body.style.top      = '-' + y + 'px';" +
                        "document.body.style.width    = '100%';",
                getElement()
        );
        addClassName("overlay--visible");
        escShortcut = Shortcuts.addShortcutListener(UI.getCurrent(), this::handleCancel, Key.ESCAPE);
    }

    private void closeToList() {
        removeClassName("overlay--visible");
        unregisterEsc();
        UI.getCurrent().getPage().executeJs(
                "var y = parseInt($0.dataset.savedScroll || '0', 10);" +
                        "document.body.style.position = '';" +
                        "document.body.style.top      = '';" +
                        "document.body.style.width    = '';" +
                        "window.scrollTo(0, y);",
                getElement()
        );
    }

    private void unregisterEsc() {
        if (escShortcut != null) {
            escShortcut.remove();
            escShortcut = null;
        }
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    private void populateView(AdvertisementInfoDto ad) {
        viewTitle.setText(ad.getTitle());
        viewDescription.setText(ad.getDescription());
    }

    private void rebuildMeta(AdvertisementInfoDto ad) {
        metaContainer.removeAll();
        metaContainer.add(metaPanelBuilder.build(
                OverlayAdvertisementMetaPanel.Parameters.builder()
                        .authorName(ad.getCreatedByUserName() != null ? ad.getCreatedByUserName() : "—")
                        .createdAt(ad.getCreatedAt())
                        .updatedAt(ad.getUpdatedAt())
                        .build()
        ));
    }

    private void handleSave() {
        if (!binder.validate().isOk()) {
            notification.error(ADVERTISEMENT_OVERLAY_NOTIFICATION_VALIDATION_FAILED);
            return;
        }
        try {
            advertisementService.save(mapper.toAdvertisement(binder.getBean()));
            notification.success(ADVERTISEMENT_OVERLAY_NOTIFICATION_SUCCESS);
            if (onSavedCallback != null) onSavedCallback.run();
            closeToList();
        } catch (Exception e) {
            notification.error(ADVERTISEMENT_OVERLAY_NOTIFICATION_SAVE_ERROR);
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