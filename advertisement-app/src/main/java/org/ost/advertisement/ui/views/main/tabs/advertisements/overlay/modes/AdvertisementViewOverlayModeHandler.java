package org.ost.advertisement.ui.views.main.tabs.advertisements.overlay.modes;

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
import org.ost.advertisement.dto.AdvertisementHistoryDto;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.entities.ActionType;
import org.ost.advertisement.security.AccessEvaluator;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.SnapshotService;
import org.ost.advertisement.ui.views.components.buttons.UiIconButton;
import org.ost.advertisement.ui.views.components.buttons.UiPrimaryButton;
import org.ost.advertisement.ui.views.components.dialogs.ConfirmActionDialog;
import org.ost.advertisement.ui.views.components.overlay.OverlayLayout;
import org.ost.advertisement.ui.views.components.overlay.OverlayModeHandler;
import org.ost.attachment.service.AttachmentService;
import org.ost.attachment.ui.AttachmentGallery;
import org.ost.advertisement.ui.views.main.tabs.advertisements.overlay.elements.OverlayAdvertisementMetaPanel;
import org.ost.advertisement.ui.views.rules.Configurable;
import org.ost.advertisement.ui.views.rules.ComponentBuilder;
import org.ost.advertisement.ui.views.rules.I18nParams;
import org.ost.advertisement.ui.views.utils.ActivityUiUtil;
import org.ost.advertisement.ui.views.utils.TimeZoneUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import org.ost.advertisement.model.ChangeEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static org.ost.advertisement.common.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class AdvertisementViewOverlayModeHandler implements OverlayModeHandler,
        Configurable<AdvertisementViewOverlayModeHandler, AdvertisementViewOverlayModeHandler.Parameters>, I18nParams {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull AdvertisementInfoDto ad;
        @NonNull Runnable             onEdit;
        @NonNull Runnable             onClose;
        @NonNull Consumer<Long>       onRestore;
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder extends ComponentBuilder<AdvertisementViewOverlayModeHandler, Parameters> {
        @Getter
        private final ObjectProvider<AdvertisementViewOverlayModeHandler> provider;
    }

    private final AccessEvaluator               access;
    @Getter
    private final I18nService                   i18nService;
    private final SnapshotService               snapshotService;
    private final ActivityUiUtil                activityUiUtil;
    private final OverlayAdvertisementMetaPanel metaPanel;
    private final UiPrimaryButton               editButton;
    private final UiIconButton                  closeButton;
    private final ObjectProvider<AttachmentGallery>   galleryProvider;
    private final ObjectProvider<AttachmentService>   attachmentServiceProvider;
    private final ConfirmActionDialog.Builder         confirmDialogBuilder;

    private Parameters params;

    @Override
    public AdvertisementViewOverlayModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    @Override
    public void activate(OverlayLayout layout) {
        Div viewContent = buildViewContent();

        Div historyContent = new Div();
        historyContent.addClassName("adv-history-content");
        historyContent.setVisible(false);

        Tab viewTab    = new Tab(getValue(ADVERTISEMENT_VIEW_TAB));
        Tab historyTab = new Tab(getValue(ADVERTISEMENT_HISTORY_TAB));
        historyTab.setVisible(access.canOperate(params.getAd()));
        Tabs tabs = new Tabs(viewTab, historyTab);
        tabs.addClassName("adv-overlay-tabs");

        tabs.addSelectedChangeListener(event -> {
            boolean isView = event.getSelectedTab() == viewTab;
            viewContent.setVisible(isView);
            historyContent.setVisible(!isView);
            if (!isView && historyContent.getChildren().findFirst().isEmpty()) {
                historyContent.add(buildHistoryContent(params.getAd().getId()));
            }
        });

        editButton.configure(UiPrimaryButton.Parameters.builder()
                .labelKey(ADVERTISEMENT_CARD_BUTTON_EDIT)
                .build());
        closeButton.configure(UiIconButton.Parameters.builder()
                .labelKey(MAIN_TAB_ADVERTISEMENTS)
                .icon(VaadinIcon.CLOSE.create())
                .build());

        editButton.addClickListener(_  -> params.getOnEdit().run());
        closeButton.addClickListener(_ -> params.getOnClose().run());
        editButton.setVisible(access.canOperate(params.getAd()));

        layout.setContent(new Div(tabs, viewContent, historyContent));
        layout.setHeaderActions(new Div(editButton, closeButton));
    }

    private Div buildViewContent() {
        H2 title = new H2(params.getAd().getTitle());
        title.addClassName("overlay__view-title");

        Span description = new Span(params.getAd().getDescription());
        description.addClassName("overlay__view-description");

        Div cardHeader = new Div(VaadinIcon.EYE.create(), new Span(getValue(ADVERTISEMENT_OVERLAY_SECTION_VIEW)));
        cardHeader.addClassName("overlay__view-card-header");

        Div textCard = new Div(cardHeader, title, description);
        textCard.addClassName("overlay__view-card");

        Div viewBody = new Div(textCard);

        galleryProvider.ifAvailable(gallery -> {
            gallery.configureForView(params.getAd().getId());
            viewBody.add(gallery);
        });

        viewBody.add(metaPanel.configure(OverlayAdvertisementMetaPanel.Parameters.from(params.getAd())));
        viewBody.addClassName("overlay__view-body");

        return viewBody;
    }

    private Div buildHistoryContent(Long adId) {
        List<AdvertisementHistoryDto> history = snapshotService.getAdvertisementHistory(
                adId, access.getCurrentUserId(), access.isPrivileged());
        Div container = new Div();
        container.addClassName("adv-history-list");

        if (history.isEmpty()) {
            Span empty = new Span(getValue(ADVERTISEMENT_HISTORY_EMPTY));
            empty.addClassName("adv-history-empty");
            container.add(empty);
            return container;
        }

        String currentTitle = params.getAd().getTitle();
        String currentDesc  = params.getAd().getDescription();
        java.util.Set<String> currentPhotoUrls = loadCurrentPhotoUrls(adId);

        for (AdvertisementHistoryDto h : history) {
            Div row = new Div();
            row.addClassName("adv-history-row");

            Span versionBadge = new Span("v" + h.version());
            versionBadge.addClassName("adv-history-version");

            Span actionBadge = new Span(formatAction(h.actionType()));
            actionBadge.addClassName("adv-history-action");
            actionBadge.addClassName("adv-history-action--" + h.actionType().name().toLowerCase());

            Span changedBy = new Span(h.changedByUserName());
            changedBy.addClassName("adv-history-user");

            Span time = new Span(TimeZoneUtil.formatInstantHuman(h.createdAt()));
            time.addClassName("adv-history-time");

            Div meta = new Div(versionBadge, actionBadge, changedBy, time);
            meta.addClassName("adv-history-meta");
            row.add(meta);

            row.add(buildFullFieldsList(h));

            // Restore button: text-based rows (prevSnapshotId != null guards photo-only rows);
            // CREATED also eligible when history has multiple entries
            boolean isTextRow = h.prevSnapshotId() != null || h.actionType() == ActionType.CREATED;
            if (access.canOperate(params.getAd()) && isTextRow
                    && (h.actionType() != ActionType.CREATED || history.size() > 1)) {

                boolean matchesCurrent = Objects.equals(h.title(), currentTitle)
                        && Objects.equals(h.description(), currentDesc)
                        && matchesCurrentPhotos(h.currAttachmentUrls(), currentPhotoUrls);

                if (matchesCurrent) {
                    Span badge = new Span(getValue(ADVERTISEMENT_HISTORY_CURRENT_STATE));
                    badge.addClassName("adv-history-current-badge");
                    row.add(badge);
                } else {
                    Button restoreBtn = new Button(getValue(ADVERTISEMENT_RESTORE_BUTTON));
                    restoreBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                    restoreBtn.addClassName("adv-history-restore-btn");
                    long snapId = h.snapshotId();
                    restoreBtn.addClickListener(_ -> showRestoreConfirm(h, snapId, currentPhotoUrls));
                    row.add(restoreBtn);
                }
            }

            container.add(row);
        }

        return container;
    }

    private Div buildFullFieldsList(AdvertisementHistoryDto h) {
        Div container = new Div();
        container.addClassName("adv-history-changes");

        boolean titleInChanges = false;
        boolean descInChanges  = false;
        boolean photoInChanges = false;

        for (ChangeEntry entry : h.changes()) {
            switch (entry) {
                case ChangeEntry.FieldChange f when "title".equals(f.field())       -> titleInChanges = true;
                case ChangeEntry.FieldChange f when "description".equals(f.field()) -> descInChanges  = true;
                case ChangeEntry.PhotoChange ignored                                 -> photoInChanges = true;
                default -> {}
            }
            String text = activityUiUtil.format(entry);
            if (text != null && !text.isBlank()) {
                Span item = new Span("• " + text);
                item.addClassName("adv-history-changes-item");
                container.add(item);
            }
        }

        if (!titleInChanges && h.title() != null) {
            Span item = new Span("• " + getValue(CHANGES_FIELD_TITLE) + ": " + h.title());
            item.addClassNames("adv-history-changes-item", "adv-history-changes-item--unchanged");
            container.add(item);
        }
        if (!descInChanges && h.description() != null) {
            String desc = h.description().length() > 60
                    ? h.description().substring(0, 60) + "…" : h.description();
            Span item = new Span("• " + getValue(CHANGES_FIELD_DESCRIPTION) + ": " + desc);
            item.addClassNames("adv-history-changes-item", "adv-history-changes-item--unchanged");
            container.add(item);
        }
        if (!photoInChanges && h.currAttachmentUrls() != null) {
            String photoLabel = getValue(CHANGES_PHOTOS);
            String photos = h.currAttachmentUrls().length == 0 ? "—"
                    : java.util.Arrays.stream(h.currAttachmentUrls()).map(AdvertisementViewOverlayModeHandler::filename)
                            .collect(java.util.stream.Collectors.joining(", "));
            Span item = new Span("• " + photoLabel + ": " + photos);
            item.addClassNames("adv-history-changes-item", "adv-history-changes-item--unchanged");
            container.add(item);
        }

        return container;
    }

    private void showRestoreConfirm(AdvertisementHistoryDto h, long snapshotId,
                                     java.util.Set<String> currentPhotoUrls) {
        List<String> lines = new ArrayList<>();

        String noChange = getValue(ADVERTISEMENT_RESTORE_NO_CHANGE);

        if (Objects.equals(params.getAd().getTitle(), h.title())) {
            lines.add(getValue(CHANGES_FIELD_TITLE) + ": " + noChange);
        } else {
            lines.add(getValue(CHANGES_FIELD_TITLE) + ": \"" + truncate(params.getAd().getTitle())
                    + "\" → \"" + truncate(h.title()) + "\"");
        }

        if (Objects.equals(params.getAd().getDescription(), h.description())) {
            lines.add(getValue(CHANGES_FIELD_DESCRIPTION) + ": " + noChange);
        } else {
            lines.add(getValue(CHANGES_FIELD_DESCRIPTION) + ": " + getValue(ADVERTISEMENT_RESTORE_CONFIRM_DESC_CHANGED));
        }

        if (h.currAttachmentUrls() != null) {
            java.util.Set<String> targetUrls = java.util.Arrays.stream(h.currAttachmentUrls())
                    .collect(java.util.stream.Collectors.toSet());
            List<String> photoLines = new ArrayList<>();
            for (String url : targetUrls) {
                String fn = filename(url);
                if (currentPhotoUrls.contains(url)) {
                    photoLines.add("  = " + fn);
                } else {
                    photoLines.add("  + " + fn);
                }
            }
            for (String url : currentPhotoUrls) {
                if (!targetUrls.contains(url)) {
                    photoLines.add("  - " + filename(url));
                }
            }
            java.util.Collections.sort(photoLines);
            if (photoLines.isEmpty()) {
                lines.add(getValue(CHANGES_PHOTOS) + ": " + noChange);
            } else {
                lines.add(getValue(CHANGES_PHOTOS) + ":\n" + String.join("\n", photoLines));
            }
        } else {
            lines.add(getValue(CHANGES_PHOTOS) + ": " + noChange);
        }

        confirmDialogBuilder.build(
                ConfirmActionDialog.Parameters.builder()
                        .titleKey(ADVERTISEMENT_RESTORE_CONFIRM_TITLE)
                        .message(String.join("\n", lines))
                        .confirmKey(ADVERTISEMENT_RESTORE_CONFIRM_BUTTON)
                        .cancelKey(ADVERTISEMENT_RESTORE_CONFIRM_CANCEL)
                        .onConfirm(() -> params.getOnRestore().accept(snapshotId))
                        .build()
        ).open();
    }

    private static String filename(String url) {
        if (url == null || url.isBlank()) return url;
        int i = url.lastIndexOf('/');
        return i >= 0 ? url.substring(i + 1) : url;
    }

    private java.util.Set<String> loadCurrentPhotoUrls(Long adId) {
        AttachmentService svc = attachmentServiceProvider.getIfAvailable();
        if (svc == null) return java.util.Set.of();
        return svc.getByEntityId(adId).stream()
                .map(org.ost.attachment.entity.Attachment::getUrl)
                .collect(java.util.stream.Collectors.toSet());
    }

    private boolean matchesCurrentPhotos(String[] currUrls, java.util.Set<String> currentUrls) {
        if (currUrls == null) return true;
        java.util.Set<String> target = java.util.Set.of(currUrls);
        return target.equals(currentUrls);
    }

}
