package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.security.AccessEvaluator;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.dto.AdvertisementEditDto;
import org.ost.advertisement.ui.mappers.AdvertisementMapper;
import org.ost.advertisement.ui.services.NotificationService;
import org.ost.advertisement.ui.utils.NotificationType;
import org.ost.advertisement.ui.views.advertisements.dialogs.fields.DialogAdvertisementDescriptionTextArea;
import org.ost.advertisement.ui.views.advertisements.dialogs.fields.DialogAdvertisementMetaPanel;
import org.ost.advertisement.ui.views.advertisements.dialogs.fields.DialogAdvertisementTitleTextField;
import org.ost.advertisement.ui.views.components.buttons.DeleteActionButton;
import org.ost.advertisement.ui.views.components.buttons.EditActionButton;
import org.ost.advertisement.ui.views.components.dialogs.ConfirmDeleteDialog;
import org.ost.advertisement.ui.views.components.dialogs.FormDialogBinder;
import org.ost.advertisement.ui.views.components.dialogs.fields.DialogPrimaryButton;
import org.ost.advertisement.ui.views.components.dialogs.fields.DialogTertiaryButton;
import org.springframework.beans.factory.ObjectProvider;

import static org.ost.advertisement.constants.I18nKey.*;

@Slf4j
@SpringComponent
@UIScope
@RequiredArgsConstructor
public class AdvertisementDetailPanel extends VerticalLayout {

    private final transient I18nService i18n;
    private final transient NotificationService notificationService;
    private final transient AdvertisementService advertisementService;
    private final transient AdvertisementMapper mapper;
    private final transient AccessEvaluator access;
    private final transient FormDialogBinder.Builder<AdvertisementEditDto> binderBuilder;
    private final transient DialogAdvertisementMetaPanel.Builder metaPanelBuilder;
    private final transient ConfirmDeleteDialog.Builder confirmDeleteDialogBuilder;
    private final transient EditActionButton.Builder editButtonBuilder;
    private final transient DeleteActionButton.Builder deleteButtonBuilder;
    private final ObjectProvider<DialogAdvertisementTitleTextField> titleFieldProvider;
    private final ObjectProvider<DialogAdvertisementDescriptionTextArea> descriptionFieldProvider;

    private enum Mode { EMPTY, READ, EDIT }

    private Mode mode = Mode.EMPTY;
    private FormDialogBinder<AdvertisementEditDto> binder;
    private Runnable onRefresh;

    // called explicitly by AdvertisementsView after construction
    public void init() {
        addClassName("advertisement-detail-panel");
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        showEmpty();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void clear() {
        showEmpty();
    }

    public void selectForRead(AdvertisementInfoDto ad, Runnable onRefresh) {
        withDirtyGuard(() -> {
            this.onRefresh = onRefresh;
            showRead(ad);
        });
    }

    public void startNew(Runnable onRefresh) {
        withDirtyGuard(() -> {
            this.onRefresh = onRefresh;
            showEdit(null);
        });
    }

    public void startEdit(AdvertisementInfoDto ad, Runnable onRefresh) {
        withDirtyGuard(() -> {
            this.onRefresh = onRefresh;
            showEdit(ad);
        });
    }

    public void confirmDelete(AdvertisementInfoDto ad, Runnable onRefresh) {
        this.onRefresh = onRefresh;
        doConfirmDelete(ad);
    }

    // ── Dirty guard ───────────────────────────────────────────────────────────

    private void withDirtyGuard(Runnable action) {
        if (mode == Mode.EDIT && binder != null && binder.getBinder().hasChanges()) {
            ConfirmDialog confirm = new ConfirmDialog();
            confirm.setHeader(i18n.get(ADVERTISEMENT_DETAIL_UNSAVED_TITLE));
            confirm.setText(i18n.get(ADVERTISEMENT_DETAIL_UNSAVED_MESSAGE));
            confirm.setConfirmText(i18n.get(ADVERTISEMENT_DETAIL_UNSAVED_DISCARD));
            confirm.setCancelText(i18n.get(ADVERTISEMENT_DETAIL_UNSAVED_STAY));
            confirm.setCancelable(true);
            confirm.addConfirmListener(_ -> action.run());
            confirm.open();
        } else {
            action.run();
        }
    }

    // ── EMPTY mode (Collapses the panel) ──────────────────────────────────────

    private void showEmpty() {
        mode = Mode.EMPTY;
        binder = null;
        removeAll();
        // Hiding the panel forces the SplitLayout to give 100% width to the left pane
        setVisible(false);
    }

    // ── READ mode ─────────────────────────────────────────────────────────────

    private void showRead(AdvertisementInfoDto ad) {
        mode = Mode.READ;
        binder = null;
        removeAll();

        // Ensure panel is visible (expands the SplitLayout)
        setVisible(true);

        // 1. Title
        H2 title = new H2(ad.getTitle());
        title.addClassName("detail-read-title");
        title.getStyle().set("margin-top", "0"); // Align nicely with top edge

        // 2. Close Button (Cross icon)
        Button closeBtn = new Button(VaadinIcon.CLOSE.create(), _ -> clear());
        closeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        closeBtn.addClassName("detail-close-button");

        // 3. Header wrapper (Relative positioning container for absolute close button)
        HorizontalLayout header = new HorizontalLayout(title, closeBtn);
        header.setWidthFull();
        header.addClassName("detail-read-header");

        // 4. Description
        Div description = new Div();
        description.addClassName("detail-read-description");
        description.setText(ad.getDescription());

        // 5. Main content wrapper
        VerticalLayout content = new VerticalLayout(header, description);
        content.addClassName("detail-read-content");
        content.setPadding(true);
        content.setSpacing(true);
        content.setWidthFull();

        HorizontalLayout actions = buildReadActions(ad);

        if (ad.getCreatedAt() != null) {
            DialogAdvertisementMetaPanel meta = metaPanelBuilder.build(
                    DialogAdvertisementMetaPanel.Parameters.builder()
                            .authorName(ad.getCreatedByUserName() != null ? ad.getCreatedByUserName() : "—")
                            .createdAt(ad.getCreatedAt())
                            .updatedAt(ad.getUpdatedAt())
                            .build());
            add(content, meta, actions);
        } else {
            add(content, actions);
        }
    }

    private HorizontalLayout buildReadActions(AdvertisementInfoDto ad) {
        HorizontalLayout actions = new HorizontalLayout();
        actions.addClassName("detail-read-actions");
        actions.setPadding(true);

        if (access.canOperate(ad)) {
            Button edit = editButtonBuilder.build(
                    EditActionButton.Config.builder()
                            .tooltip(i18n.get(ADVERTISEMENT_CARD_BUTTON_EDIT))
                            .onClick(() -> showEdit(ad))
                            .small(false)
                            .build()
            );
            Button delete = deleteButtonBuilder.build(
                    DeleteActionButton.Config.builder()
                            .tooltip(i18n.get(ADVERTISEMENT_CARD_BUTTON_DELETE))
                            .onClick(() -> doConfirmDelete(ad))
                            .small(false)
                            .build()
            );
            actions.add(edit, delete);
        }
        return actions;
    }

    // ── EDIT mode ─────────────────────────────────────────────────────────────

    private void showEdit(AdvertisementInfoDto ad) {
        mode = Mode.EDIT;
        removeAll();

        // Ensure panel is visible (expands the SplitLayout)
        setVisible(true);

        DialogAdvertisementTitleTextField titleField = titleFieldProvider.getObject();
        DialogAdvertisementDescriptionTextArea descriptionField = descriptionFieldProvider.getObject();

        AdvertisementEditDto dto = ad == null
                ? new AdvertisementEditDto()
                : mapper.toAdvertisementEdit(ad);

        binder = binderBuilder.build(FormDialogBinder.Config.<AdvertisementEditDto>builder()
                .clazz(AdvertisementEditDto.class)
                .dto(dto)
                .build());

        binder.getBinder().forField(titleField)
                .asRequired(i18n.get(ADVERTISEMENT_DIALOG_VALIDATION_TITLE_REQUIRED))
                .bind(AdvertisementEditDto::getTitle, AdvertisementEditDto::setTitle);

        binder.getBinder().forField(descriptionField)
                .asRequired(i18n.get(ADVERTISEMENT_DIALOG_VALIDATION_DESCRIPTION_REQUIRED))
                .bind(AdvertisementEditDto::getDescription, AdvertisementEditDto::setDescription);

        DialogPrimaryButton saveButton = new DialogPrimaryButton(
                DialogPrimaryButton.Parameters.builder()
                        .i18n(i18n).labelKey(ADVERTISEMENT_DIALOG_BUTTON_SAVE).build());
        saveButton.addClickListener(_ -> handleSave());

        DialogTertiaryButton cancelButton = new DialogTertiaryButton(
                DialogTertiaryButton.Parameters.builder()
                        .i18n(i18n).labelKey(ADVERTISEMENT_DIALOG_BUTTON_CANCEL).build());
        cancelButton.addClickListener(_ -> {
            if (ad != null) showRead(ad);
            else showEmpty();
        });

        VerticalLayout form = new VerticalLayout(titleField, descriptionField);
        form.addClassName("detail-edit-form");
        form.setPadding(true);
        form.setWidthFull();

        HorizontalLayout actions = new HorizontalLayout(saveButton, cancelButton);
        actions.addClassName("detail-edit-actions");
        actions.setPadding(true);

        add(form, actions);
    }

    private void handleSave() {
        boolean saved = binder.save(dto -> advertisementService.save(mapper.toAdvertisement(dto)));
        if (saved) {
            notificationService.show(NotificationType.SUCCESS, ADVERTISEMENT_DIALOG_NOTIFICATION_SUCCESS);
            if (onRefresh != null) onRefresh.run();
            showEmpty();
        } else {
            notificationService.show(NotificationType.ERROR, ADVERTISEMENT_DIALOG_NOTIFICATION_SAVE_ERROR);
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    private void doConfirmDelete(AdvertisementInfoDto ad) {
        confirmDeleteDialogBuilder.build(
                USER_VIEW_CONFIRM_DELETE_TITLE,
                i18n.get(ADVERTISEMENT_VIEW_CONFIRM_DELETE_TEXT, ad.getTitle(), ad.getId()),
                ADVERTISEMENT_VIEW_CONFIRM_DELETE_BUTTON,
                ADVERTISEMENT_VIEW_CONFIRM_CANCEL_BUTTON,
                () -> {
                    try {
                        advertisementService.delete(ad);
                        notificationService.show(NotificationType.SUCCESS, ADVERTISEMENT_VIEW_NOTIFICATION_DELETED);
                        if (onRefresh != null) onRefresh.run();
                        showEmpty();
                    } catch (Exception ex) {
                        log.error("Error deleting advertisement id={}", ad.getId(), ex);
                        notificationService.show(NotificationType.ERROR, ADVERTISEMENT_VIEW_NOTIFICATION_DELETE_ERROR, ex.getMessage());
                    }
                }
        ).open();
    }
}