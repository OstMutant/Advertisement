package org.ost.marketplace.ui.views.main.tabs.moderation;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.orchestrator.services.FeedbackAccessService;
import org.ost.orchestrator.services.ProviderProfileDisplayEnrichmentService;
import org.ost.orchestrator.services.ProviderProfileReadService;
import org.ost.marketplace.ui.query.QueryBlock;
import org.ost.marketplace.ui.query.QueryStatusBar;
import org.ost.platform.feedback.dto.ModerationQueueFilterDto;
import org.ost.platform.feedback.dto.ModerationQueueItemDto;
import org.springframework.data.domain.Sort;
import org.ost.platform.feedback.model.FeedbackModerationStatus;
import org.ost.platform.feedback.model.ModerationQueueItemType;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.marketplace.ui.views.components.PaginationBar;
import org.ost.marketplace.ui.views.services.pagination.SettingsPaginationBinding;
import org.ost.platform.user.dto.UserSettingsDto;

import java.util.List;
import java.util.function.Consumer;

import static org.ost.marketplace.services.i18n.I18nKey.MODERATION_BUTTON_APPROVE;
import static org.ost.marketplace.services.i18n.I18nKey.MODERATION_FIELD_AUTHOR;
import static org.ost.marketplace.services.i18n.I18nKey.MODERATION_FIELD_CONTENT;
import static org.ost.marketplace.services.i18n.I18nKey.MODERATION_FIELD_REASON;
import static org.ost.marketplace.services.i18n.I18nKey.MODERATION_FIELD_REPORTED;
import static org.ost.marketplace.services.i18n.I18nKey.MODERATION_FIELD_REPORTED_PROFILE;
import static org.ost.marketplace.services.i18n.I18nKey.MODERATION_FIELD_STATUS;
import static org.ost.marketplace.services.i18n.I18nKey.MODERATION_FIELD_TYPE;
import static org.ost.marketplace.services.i18n.I18nKey.MODERATION_BUTTON_CONFIRM_VIOLATION;
import static org.ost.marketplace.services.i18n.I18nKey.MODERATION_EMPTY;
import static org.ost.marketplace.services.i18n.I18nKey.MODERATION_NOTIFICATION_APPROVED;
import static org.ost.marketplace.services.i18n.I18nKey.MODERATION_NOTIFICATION_CONFIRMED;
import static org.ost.marketplace.services.i18n.I18nKey.MODERATION_NOTIFICATION_ERROR;

/** The Moderation tab -- newest-first merged queue of flagged feedback entries and replies, visible only to MODERATOR/ADMIN. Rendered as cards (not a Grid) since field content is too long for grid cells. */
@Slf4j
@SpringComponent
@UIScope
@RequiredArgsConstructor
public class ModerationView extends VerticalLayout {

    private final transient FeedbackAccessService              feedbackAccessService;
    private final transient AccessEvaluator                    access;
    private final transient I18nService                        i18n;
    private final transient NotificationService                notificationService;
    private final transient ProviderProfileReadService         providerProfileReadService;
    private final transient ProviderProfileDisplayEnrichmentService enrichmentService;
    private final transient PaginationBar                      paginationBar;
    private final transient QueryStatusBar<ModerationQueueFilterDto> queryStatusBar;
    private final transient SettingsPaginationBinding           settingsPaginationBinding;

    private Div listContainer;
    private Span emptyStateLabel;
    @SuppressWarnings("java:S1450")
    private transient Consumer<Long> onOpenProviderProfile;

    @PostConstruct
    protected void init() {
        addClassName("moderation-view-layout");
        setWidthFull();

        listContainer = new Div();
        listContainer.addClassName("moderation-list");

        emptyStateLabel = new Span(i18n.get(MODERATION_EMPTY));
        emptyStateLabel.addClassName("moderation-empty-label");
        emptyStateLabel.setVisible(false);

        VerticalLayout contentWrapper = new VerticalLayout(
                queryStatusBar, queryStatusBar.getQueryBlock(), listContainer, emptyStateLabel, paginationBar
        );
        contentWrapper.setPadding(false);
        contentWrapper.setSpacing(false);
        contentWrapper.setWidthFull();
        contentWrapper.addClassName("moderation-content-wrapper");
        add(contentWrapper);

        queryStatusBar.getQueryBlock().addEventListener(() -> {
            paginationBar.resetToFirstPage();
            refresh();
        });

        paginationBar.setPageChangeListener(_ -> refresh());
        settingsPaginationBinding.register(paginationBar, UserSettingsDto::getModerationPageSize, this::refresh);

        refresh();
    }

    /** Called once by {@code MainView} at wiring time -- fires with a provider-profile id whenever a card's profile link is clicked; the caller owns tab-switching and overlay-opening, this view only reports the click. */
    public void setOnOpenProviderProfile(Consumer<Long> onOpenProviderProfile) {
        this.onOpenProviderProfile = onOpenProviderProfile;
    }

    // MainView calls this on every tab select -- tabs only toggle visibility, never re-fetch on their own.
    public void refreshOnTabSelect() {
        refresh();
    }

    private void refresh() {
        try {
            QueryBlock<ModerationQueueFilterDto> queryBlock = queryStatusBar.getQueryBlock();
            ModerationQueueFilterDto filter = queryBlock.getFilterProcessor().getOriginalFilter();
            Sort sort = queryBlock.getSortProcessor().getOriginalSort().getSort();
            FeedbackAccessService.ModerationQueuePage result =
                    feedbackAccessService.findModerationQueue(access.getCurrentUserId(), filter, sort, paginationBar.getCurrentPage(), paginationBar.getPageSize());
            listContainer.removeAll();
            result.items().forEach(item -> listContainer.add(buildCard(item)));
            emptyStateLabel.setVisible(result.items().isEmpty());
            paginationBar.setTotalCount(result.totalCount());
        } catch (Exception ex) {
            log.error("Failed to refresh moderation queue", ex);
            notificationService.error(MODERATION_NOTIFICATION_ERROR, ex.getMessage());
            listContainer.removeAll();
            emptyStateLabel.setVisible(true);
            paginationBar.setTotalCount(0);
        } finally {
            queryStatusBar.update();
        }
    }

    private Div buildCard(ModerationQueueItemDto item) {
        Div card = new Div();
        card.addClassName("feedback-entry");
        card.addClassName("moderation-card");

        card.add(buildFieldRow(i18n.get(MODERATION_FIELD_REPORTED_PROFILE), buildReportedProfileValue(item)));
        card.add(buildFieldRow(i18n.get(MODERATION_FIELD_TYPE), new Span(item.getType().name())));
        card.add(buildFieldRow(i18n.get(MODERATION_FIELD_STATUS), new Span(item.getModerationStatus().name())));
        card.add(buildFieldRow(i18n.get(MODERATION_FIELD_CONTENT), buildContentValue(item)));
        card.add(buildFieldRow(i18n.get(MODERATION_FIELD_AUTHOR), buildAuthorValue(item)));
        card.add(buildFieldRow(i18n.get(MODERATION_FIELD_REASON), new Span(item.getModerationReason())));
        card.add(buildFieldRow(i18n.get(MODERATION_FIELD_REPORTED), new Span(String.valueOf(item.getCreatedAt()))));
        card.add(buildActionRow(item));
        return card;
    }

    private Div buildFieldRow(String label, com.vaadin.flow.component.Component value) {
        Div row = new Div();
        row.addClassName("moderation-field-row");
        Span labelSpan = new Span(label + ":");
        labelSpan.addClassName("moderation-field-label");
        row.add(labelSpan, value);
        return row;
    }

    private Span buildContentValue(ModerationQueueItemDto item) {
        String text = item.getType() == ModerationQueueItemType.FEEDBACK && item.getRating() != null
                ? "★".repeat(item.getRating()) + " " + item.getContentText()
                : item.getContentText();
        return new Span(text);
    }

    private Span buildReportedProfileValue(ModerationQueueItemDto item) {
        return providerProfileReadService.findById(item.getEntityId())
                .map(enrichmentService::enrichWithActor)
                .map(profile -> buildProfileLink(profile.getId(), profile.getActorName()))
                .orElseGet(() -> new Span("—"));
    }

    private Span buildAuthorValue(ModerationQueueItemDto item) {
        return providerProfileReadService.findByActorId(item.getAuthorId())
                .map(profile -> buildProfileLink(profile.getId(), item.getAuthorName()))
                .orElseGet(() -> new Span(item.getAuthorName()));
    }

    private Span buildProfileLink(Long providerProfileId, String label) {
        Span link = new Span(label);
        link.addClassName("moderation-profile-link");
        link.getElement().addEventListener("click", _ -> {
            if (onOpenProviderProfile != null) onOpenProviderProfile.accept(providerProfileId);
        });
        return link;
    }

    private Div buildActionRow(ModerationQueueItemDto item) {
        UiIconButton approveButton = new UiIconButton(i18n.get(MODERATION_BUTTON_APPROVE), VaadinIcon.CHECK.create());
        approveButton.addClassName("moderation-approve-icon");
        approveButton.addClickListener(_ -> approve(item));

        UiIconButton confirmButton = new UiIconButton(i18n.get(MODERATION_BUTTON_CONFIRM_VIOLATION), VaadinIcon.BAN.create());
        confirmButton.addClassName("moderation-confirm-violation-icon");
        confirmButton.addClickListener(_ -> confirmViolation(item));

        Div actions = item.getModerationStatus() == FeedbackModerationStatus.CONFIRMED_HIDDEN
                ? new Div(approveButton)
                : new Div(approveButton, confirmButton);
        actions.addClassName("moderation-actions");
        return actions;
    }

    private void approve(ModerationQueueItemDto item) {
        try {
            if (item.getType() == ModerationQueueItemType.FEEDBACK) {
                feedbackAccessService.approveFeedback(item.getId(), access.getCurrentUserId());
            } else {
                feedbackAccessService.approveComment(item.getId(), access.getCurrentUserId());
            }
            notificationService.success(MODERATION_NOTIFICATION_APPROVED);
            refresh();
        } catch (Exception ex) {
            log.error("Error approving moderation queue item id={} type={}", item.getId(), item.getType(), ex);
            notificationService.error(MODERATION_NOTIFICATION_ERROR, ex.getMessage());
        }
    }

    private void confirmViolation(ModerationQueueItemDto item) {
        try {
            if (item.getType() == ModerationQueueItemType.FEEDBACK) {
                feedbackAccessService.confirmFeedbackViolation(item.getId(), access.getCurrentUserId());
            } else {
                feedbackAccessService.confirmCommentViolation(item.getId(), access.getCurrentUserId());
            }
            notificationService.success(MODERATION_NOTIFICATION_CONFIRMED);
            refresh();
        } catch (Exception ex) {
            log.error("Error confirming moderation queue item id={} type={}", item.getId(), item.getType(), ex);
            notificationService.error(MODERATION_NOTIFICATION_ERROR, ex.getMessage());
        }
    }
}
