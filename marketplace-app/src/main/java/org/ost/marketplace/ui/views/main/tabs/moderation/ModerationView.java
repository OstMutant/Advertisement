package org.ost.marketplace.ui.views.main.tabs.moderation;

import com.vaadin.flow.component.grid.Grid;
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
import org.ost.marketplace.ui.views.components.dialogs.ConfirmActionDialog;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.orchestrator.services.FeedbackAccessService;
import org.ost.platform.feedback.dto.FeedbackCommentDto;
import org.ost.platform.feedback.dto.FeedbackDto;
import org.springframework.data.domain.PageRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.ost.marketplace.services.i18n.I18nKey.MODERATION_BUTTON_APPROVE;
import static org.ost.marketplace.services.i18n.I18nKey.MODERATION_EMPTY;
import static org.ost.marketplace.services.i18n.I18nKey.MODERATION_NOTIFICATION_APPROVED;
import static org.ost.marketplace.services.i18n.I18nKey.MODERATION_NOTIFICATION_ERROR;
import static org.ost.marketplace.services.i18n.I18nKey.MODERATION_SECTION_COMMENTS;
import static org.ost.marketplace.services.i18n.I18nKey.MODERATION_SECTION_FEEDBACK;

/** The Moderation tab — oldest-first queue of flagged feedback entries and comments, visible only to MODERATOR/ADMIN. */
@Slf4j
@SpringComponent
@UIScope
@RequiredArgsConstructor
public class ModerationView extends VerticalLayout {

    private final transient FeedbackAccessService feedbackAccessService;
    private final transient AccessEvaluator        access;
    private final transient I18nService            i18n;
    private final transient NotificationService    notificationService;

    private Grid<FeedbackDto>        feedbackGrid;
    private Grid<FeedbackCommentDto> commentGrid;
    private Span emptyStateLabel;
    private Map<Long, String> authorNamesById = Map.of();
    private boolean feedbackGridEmpty = true;
    private boolean commentGridEmpty = true;

    @PostConstruct
    protected void init() {
        addClassName("moderation-view-layout");
        setWidthFull();

        feedbackGrid = new Grid<>(FeedbackDto.class, false);
        feedbackGrid.addClassName("moderation-feedback-grid");
        feedbackGrid.setAllRowsVisible(true);
        configureFeedbackGridColumns();

        commentGrid = new Grid<>(FeedbackCommentDto.class, false);
        commentGrid.addClassName("moderation-comment-grid");
        commentGrid.setAllRowsVisible(true);
        configureCommentGridColumns();

        emptyStateLabel = new Span(i18n.get(MODERATION_EMPTY));
        emptyStateLabel.addClassName("moderation-empty-label");
        emptyStateLabel.setVisible(false);

        VerticalLayout contentWrapper = new VerticalLayout(
                new Span(i18n.get(MODERATION_SECTION_FEEDBACK)),
                feedbackGrid,
                new Span(i18n.get(MODERATION_SECTION_COMMENTS)),
                commentGrid,
                emptyStateLabel
        );
        contentWrapper.setPadding(false);
        contentWrapper.setSpacing(false);
        contentWrapper.setWidthFull();
        add(contentWrapper);

        refresh();
    }

    // MainView calls this on every tab select -- tabs only toggle visibility, never re-fetch on their own.
    public void refreshOnTabSelect() {
        refresh();
    }

    private void configureFeedbackGridColumns() {
        feedbackGrid.addColumn(entry -> authorNamesById.getOrDefault(entry.authorId(), String.valueOf(entry.authorId()))).setHeader("Author").setKey("author");
        feedbackGrid.addColumn(FeedbackDto::feedbackText).setHeader("Text");
        feedbackGrid.addColumn(FeedbackDto::createdAt).setHeader("Created");
        feedbackGrid.addComponentColumn(entry -> buildActionButtons(() -> approveFeedback(entry.id())));
    }

    private void configureCommentGridColumns() {
        commentGrid.addColumn(comment -> authorNamesById.getOrDefault(comment.authorId(), String.valueOf(comment.authorId()))).setHeader("Author").setKey("author");
        commentGrid.addColumn(FeedbackCommentDto::commentText).setHeader("Text");
        commentGrid.addColumn(FeedbackCommentDto::createdAt).setHeader("Created");
        commentGrid.addComponentColumn(comment -> buildActionButtons(() -> approveComment(comment.id())));
    }

    private Div buildActionButtons(Runnable onApprove) {
        UiIconButton approveButton = new UiIconButton(i18n.get(MODERATION_BUTTON_APPROVE), VaadinIcon.CHECK.create());
        approveButton.addClassName("moderation-approve-icon");
        approveButton.addClickListener(_ -> onApprove.run());

        Div actions = new Div(approveButton);
        actions.addClassName("moderation-actions");
        return actions;
    }

    private void approveFeedback(Long feedbackId) {
        try {
            feedbackAccessService.approveFeedback(feedbackId, access.getCurrentUserId());
            notificationService.success(MODERATION_NOTIFICATION_APPROVED);
            refreshFeedbackGrid();
        } catch (Exception ex) {
            log.error("Error approving feedback id={}", feedbackId, ex);
            notificationService.error(MODERATION_NOTIFICATION_ERROR, ex.getMessage());
        }
    }

    private void approveComment(Long commentId) {
        try {
            feedbackAccessService.approveComment(commentId, access.getCurrentUserId());
            notificationService.success(MODERATION_NOTIFICATION_APPROVED);
            refreshCommentGrid();
        } catch (Exception ex) {
            log.error("Error approving comment id={}", commentId, ex);
            notificationService.error(MODERATION_NOTIFICATION_ERROR, ex.getMessage());
        }
    }

    private void refresh() {
        refreshFeedbackGrid();
        refreshCommentGrid();
    }

    private void refreshFeedbackGrid() {
        try {
            List<FeedbackDto> hiddenFeedback = feedbackAccessService.findHiddenFeedback(PageRequest.of(0, 50));
            Set<Long> authorIds = hiddenFeedback.stream().map(FeedbackDto::authorId).collect(Collectors.toSet());
            if (!authorIds.isEmpty()) {
                Map<Long, String> merged = new HashMap<>(authorNamesById);
                merged.putAll(feedbackAccessService.resolveAuthorNames(authorIds));
                authorNamesById = merged;
            }
            feedbackGrid.setItems(hiddenFeedback);
            feedbackGridEmpty = hiddenFeedback.isEmpty();
            emptyStateLabel.setVisible(feedbackGridEmpty && commentGridEmpty);
        } catch (Exception ex) {
            log.error("Failed to refresh feedback moderation queue", ex);
            notificationService.error(MODERATION_NOTIFICATION_ERROR, ex.getMessage());
            feedbackGrid.setItems(List.of());
        }
    }

    private void refreshCommentGrid() {
        try {
            List<FeedbackCommentDto> hiddenComments = feedbackAccessService.findHiddenComments(PageRequest.of(0, 50));
            Set<Long> authorIds = hiddenComments.stream().map(FeedbackCommentDto::authorId).collect(Collectors.toSet());
            if (!authorIds.isEmpty()) {
                Map<Long, String> merged = new HashMap<>(authorNamesById);
                merged.putAll(feedbackAccessService.resolveAuthorNames(authorIds));
                authorNamesById = merged;
            }
            commentGrid.setItems(hiddenComments);
            commentGridEmpty = hiddenComments.isEmpty();
            emptyStateLabel.setVisible(feedbackGridEmpty && commentGridEmpty);
        } catch (Exception ex) {
            log.error("Failed to refresh comment moderation queue", ex);
            notificationService.error(MODERATION_NOTIFICATION_ERROR, ex.getMessage());
            commentGrid.setItems(List.of());
        }
    }
}
