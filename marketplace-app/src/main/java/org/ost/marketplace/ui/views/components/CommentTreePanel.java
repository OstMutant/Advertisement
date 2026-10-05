package org.ost.marketplace.ui.views.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.core.Configurable;
import org.ost.marketplace.ui.core.Initialization;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.components.dialogs.ConfirmActionDialog;
import org.ost.marketplace.ui.views.components.fields.UiTextArea;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.ost.orchestrator.services.FeedbackAccessService;
import org.ost.platform.feedback.dto.FeedbackCommentDto;
import org.ost.platform.feedback.dto.FeedbackCommentReactionSaveDto;
import org.ost.platform.feedback.dto.FeedbackCommentSaveDto;
import org.ost.platform.feedback.model.FeedbackModerationStatus;
import org.ost.platform.feedback.model.FeedbackReactionType;
import org.springframework.context.annotation.Scope;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_BUTTON_REPORT;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_COMMENT_BUTTON_REPLY;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_COMMENT_CENSORED_TEXT;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_COMMENT_CONFIRM_CANCEL_BUTTON;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_COMMENT_CONFIRM_DELETE_BUTTON;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_COMMENT_CONFIRM_DELETE_TEXT;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_COMMENT_CONFIRM_DELETE_TITLE;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_COMMENT_DELETED_TEXT;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_COMMENT_FIELD_TEXT;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_COMMENT_HIDE_REPLIES;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_COMMENT_SHOW_REPLIES;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_CONFIRM_REPORT_BUTTON;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_CONFIRM_REPORT_CANCEL_BUTTON;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_CONFIRM_REPORT_TEXT;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_CONFIRM_REPORT_TITLE;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_FORM_BUTTON_CLOSE;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_FORM_BUTTON_DISCARD;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_FORM_BUTTON_EDIT;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_FORM_BUTTON_SUBMIT;
import static org.ost.marketplace.services.i18n.I18nKey.USER_VIEW_BUTTON_DELETE;

/** Threaded reply tree for one feedback entry -- reply/edit/delete/react inline, no persistent side form. */
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class CommentTreePanel extends Div
        implements Configurable<CommentTreePanel, CommentTreePanel.Parameters>, Initialization<CommentTreePanel>, I18nParams {

    private static final Duration EDIT_WINDOW = Duration.ofHours(48);

    public record Parameters(@NonNull Long feedbackId) {
    }

    private final FeedbackAccessService feedbackAccessService;
    private final AccessEvaluator       access;
    @Getter
    private final I18nService           i18nService;

    private Long feedbackId;
    private Map<Long, String> namesById = Map.of();
    private Div topLevelReplySlot;
    private final Set<Long> expandedCommentIds = new HashSet<>();
    private final Map<Long, String> draftReplyText = new HashMap<>();
    private final Map<Long, String> draftEditText = new HashMap<>();
    private final Set<Long> openReplyComposerFor = new HashSet<>();
    private final Set<Long> openEditComposerFor = new HashSet<>();
    private Div topLevelCommentsContainer;
    private int topLevelCommentCount;
    private Runnable onChanged;

    @Override
    @PostConstruct
    public CommentTreePanel init() {
        addClassName("comment-tree-panel");
        return this;
    }

    @Override
    public CommentTreePanel configure(Parameters p) {
        removeAll();
        feedbackId = p.feedbackId();

        Long viewerId = access.isLoggedIn() ? access.getCurrentUserId() : null;
        List<FeedbackCommentDto> comments = feedbackAccessService.findCommentsByFeedback(feedbackId, viewerId);

        Set<Long> namesNeeded = new HashSet<>();
        comments.forEach(comment -> {
            namesNeeded.add(comment.authorId());
            comment.reactorIdsByType().values().forEach(namesNeeded::addAll);
        });
        namesById = namesNeeded.isEmpty() ? Map.of() : feedbackAccessService.resolveAuthorNames(namesNeeded);

        Map<Long, List<FeedbackCommentDto>> byParent = comments.stream()
                .collect(Collectors.groupingBy(c -> c.parentCommentId() == null ? 0L : c.parentCommentId()));

        List<FeedbackCommentDto> topLevelComments = byParent.getOrDefault(0L, List.of());
        topLevelCommentCount = topLevelComments.size();
        if (!topLevelComments.isEmpty()) {
            topLevelCommentsContainer = new Div();
            topLevelCommentsContainer.addClassName("comment-children");
            topLevelCommentsContainer.setVisible(expandedCommentIds.contains(0L));
            topLevelComments.forEach(comment -> topLevelCommentsContainer.add(buildNode(comment, byParent, 1)));
            add(topLevelCommentsContainer);
        } else {
            topLevelCommentsContainer = null;
        }

        topLevelReplySlot = new Div();
        topLevelReplySlot.addClassName("comment-reply-form-slot");
        topLevelReplySlot.getStyle().set("margin-left", "20px");
        topLevelReplySlot.add(buildTopLevelReplyTrigger());
        add(topLevelReplySlot);
        if (onChanged != null) {
            onChanged.run();
        }
        return this;
    }

    private Div buildTopLevelReplyTrigger() {
        Div trigger = new Div();
        trigger.addClassName("comment-reply-trigger");
        UiIconButton triggerButton = new UiIconButton(getValue(FEEDBACK_COMMENT_BUTTON_REPLY), VaadinIcon.REPLY.create());
        triggerButton.addClassName("comment-reply-icon");
        triggerButton.addClickListener(_ -> preserveScroll(() -> {
            if (openReplyComposerFor.contains(0L)) {
                topLevelReplySlot.removeAll();
                topLevelReplySlot.add(buildTopLevelReplyTrigger());
                openReplyComposerFor.remove(0L);
            } else {
                topLevelReplySlot.removeAll();
                topLevelReplySlot.add(buildReplyComposer(null));
                openReplyComposerFor.add(0L);
                if (topLevelCommentsContainer != null) {
                    topLevelCommentsContainer.setVisible(true);
                    expandedCommentIds.add(0L);
                }
            }
        }));
        trigger.add(triggerButton);
        return trigger;
    }

    private Div buildReplyTrigger(Long parentCommentId) {
        Div trigger = new Div();
        trigger.addClassName("comment-reply-trigger");
        UiIconButton triggerButton = new UiIconButton(getValue(FEEDBACK_COMMENT_BUTTON_REPLY), VaadinIcon.REPLY.create());
        triggerButton.addClassName("comment-reply-icon");
        triggerButton.addClickListener(_ -> preserveScroll(() -> {
            // This button is only accessed when the parent is found in buildNode, so we can safely cast here
            if (trigger.getParent().isPresent()) {
                Div replySlot = (Div) trigger.getParent().get();
                if (openReplyComposerFor.contains(parentCommentId)) {
                    replySlot.removeAll();
                    replySlot.add(buildReplyTrigger(parentCommentId));
                    openReplyComposerFor.remove(parentCommentId);
                } else {
                    replySlot.removeAll();
                    replySlot.add(buildReplyComposer(parentCommentId));
                    openReplyComposerFor.add(parentCommentId);
                    expandedCommentIds.add(parentCommentId);
                }
            }
        }));
        trigger.add(triggerButton);
        return trigger;
    }

    private Div buildNode(FeedbackCommentDto comment, Map<Long, List<FeedbackCommentDto>> byParent, int depth) {
        Div node = new Div();
        node.addClassName("comment-node");
        node.getStyle().set("margin-left", (depth * 20) + "px");

        boolean tombstoned = comment.commentText() == null;

        Div header = new Div();
        header.addClassName("comment-header");
        Span author = new Span(namesById.getOrDefault(comment.authorId(), ""));
        author.addClassName("comment-author");
        header.add(author);

        Div textContainer = new Div();
        textContainer.addClassName("comment-text-container");
        renderCommentText(textContainer, comment, tombstoned);

        Div editFormSlot = new Div();
        editFormSlot.addClassName("comment-edit-form-slot");

        Div replyFormSlot = new Div();
        replyFormSlot.addClassName("comment-reply-form-slot");
        replyFormSlot.getStyle().set("margin-left", ((depth + 1) * 20) + "px");
        if (openReplyComposerFor.contains(comment.id())) {
            replyFormSlot.add(buildReplyComposer(comment.id()));
        } else {
            replyFormSlot.add(buildReplyTrigger(comment.id()));
        }

        List<FeedbackCommentDto> children = byParent.getOrDefault(comment.id(), List.of());
        Div childrenContainer = new Div();
        childrenContainer.addClassName("comment-children");
        children.forEach(child -> childrenContainer.add(buildNode(child, byParent, depth + 1)));

        header.add(buildActionsRow(comment, tombstoned, byParent, textContainer, editFormSlot, replyFormSlot, childrenContainer, children.size()));
        node.add(header);
        node.add(textContainer);
        node.add(editFormSlot);
        node.add(replyFormSlot);
        node.add(childrenContainer);

        return node;
    }

    private void renderCommentText(Div textContainer, FeedbackCommentDto comment, boolean tombstoned) {
        textContainer.removeAll();
        if (tombstoned) {
            Span deleted = new Span(getValue(FEEDBACK_COMMENT_DELETED_TEXT));
            deleted.addClassName("comment-deleted-text");
            textContainer.add(deleted);
            return;
        }
        if (comment.moderationStatus() == FeedbackModerationStatus.HIDDEN) {
            Span censored = new Span(getValue(FEEDBACK_COMMENT_CENSORED_TEXT));
            censored.addClassName("comment-censored-text");
            textContainer.add(censored);
            return;
        }
        Div text = new Div();
        text.addClassName("comment-text");
        text.getElement().setProperty("innerHTML", comment.commentText());
        textContainer.add(text);
    }

    private Div buildActionsRow(FeedbackCommentDto comment, boolean tombstoned, Map<Long, List<FeedbackCommentDto>> byParent,
                                 Div textContainer, Div editFormSlot, Div replyFormSlot,
                                 Div childrenContainer, int childrenCount) {
        Div actions = new Div();
        actions.addClassName("comment-actions");

        boolean ownComment = !tombstoned && access.isLoggedIn() && comment.authorId().equals(access.getCurrentUserId())
                && Instant.now().isBefore(comment.createdAt().plus(EDIT_WINDOW));

        boolean canReport = !tombstoned && access.isLoggedIn() && !comment.authorId().equals(access.getCurrentUserId()) && comment.moderationStatus() == FeedbackModerationStatus.NEW;

        Div reactions = new Div();
        reactions.addClassName("comment-actions-reactions");
        if (!tombstoned) {
            reactions.add(buildReactionButton(comment, FeedbackReactionType.UP, VaadinIcon.THUMBS_UP));
            reactions.add(buildReactionButton(comment, FeedbackReactionType.DOWN, VaadinIcon.THUMBS_DOWN));
        }

        Div divider = new Div();
        divider.addClassName("comment-actions-divider");

        Div controls = new Div();
        controls.addClassName("comment-actions-controls");

        boolean canSeeReport = !tombstoned && access.isLoggedIn() && !comment.authorId().equals(access.getCurrentUserId());

        if (canSeeReport) {
            UiIconButton reportButton = new UiIconButton(getValue(FEEDBACK_BUTTON_REPORT), VaadinIcon.FLAG.create());
            reportButton.addClassName("comment-report-icon");
            reportButton.setEnabled(canReport);
            reportButton.addClickListener(_ -> new ConfirmActionDialog(
                    getValue(FEEDBACK_CONFIRM_REPORT_TITLE),
                    getValue(FEEDBACK_CONFIRM_REPORT_TEXT),
                    getValue(FEEDBACK_CONFIRM_REPORT_BUTTON),
                    getValue(FEEDBACK_CONFIRM_REPORT_CANCEL_BUTTON),
                    () -> {
                        feedbackAccessService.flagComment(comment.id(), access.getCurrentUserId());
                        reload();
                    }
            ).open());
            controls.add(reportButton);
        }

        if (ownComment) {
            UiIconButton editButton = new UiIconButton(getValue(FEEDBACK_FORM_BUTTON_EDIT), VaadinIcon.EDIT.create());
            editButton.addClassName("comment-edit-icon");
            editButton.addClickListener(_ -> preserveScroll(() -> {
                if (openEditComposerFor.contains(comment.id())) {
                    editFormSlot.removeAll();
                    textContainer.setVisible(true);
                    openEditComposerFor.remove(comment.id());
                    draftEditText.remove(comment.id());
                } else {
                    editFormSlot.add(buildEditComposer(comment, textContainer, editFormSlot));
                    textContainer.setVisible(false);
                    openEditComposerFor.add(comment.id());
                }
            }));
            controls.add(editButton);

            UiIconButton deleteButton = new UiIconButton(getValue(USER_VIEW_BUTTON_DELETE), VaadinIcon.TRASH.create());
            deleteButton.addClassName("comment-delete-icon");
            deleteButton.addClickListener(_ -> new ConfirmActionDialog(
                    getValue(FEEDBACK_COMMENT_CONFIRM_DELETE_TITLE),
                    getValue(FEEDBACK_COMMENT_CONFIRM_DELETE_TEXT),
                    getValue(FEEDBACK_COMMENT_CONFIRM_DELETE_BUTTON),
                    getValue(FEEDBACK_COMMENT_CONFIRM_CANCEL_BUTTON),
                    () -> {
                        feedbackAccessService.deleteComment(comment.id());
                        reload();
                    }
            ).open());
            controls.add(deleteButton);
        }

        if (childrenCount > 0) {
            boolean expanded = expandedCommentIds.contains(comment.id());
            childrenContainer.setVisible(expanded);
            UiIconButton toggle = new UiIconButton(
                    getValue(expanded ? FEEDBACK_COMMENT_HIDE_REPLIES : FEEDBACK_COMMENT_SHOW_REPLIES, childrenCount),
                    (expanded ? VaadinIcon.CHEVRON_UP : VaadinIcon.CHEVRON_DOWN).create());
            toggle.addClassName("comment-toggle-icon");
            Span toggleCount = new Span(String.valueOf(childrenCount));
            toggleCount.addClassName("comment-toggle-count");
            toggle.addClickListener(_ -> {
                boolean nowVisible = !childrenContainer.isVisible();
                childrenContainer.setVisible(nowVisible);
                if (nowVisible) {
                    expandedCommentIds.add(comment.id());
                    collectDescendantIds(comment.id(), byParent, expandedCommentIds);
                    forceExpandDescendants(childrenContainer);
                } else {
                    expandedCommentIds.remove(comment.id());
                }
                String label = getValue(nowVisible ? FEEDBACK_COMMENT_HIDE_REPLIES : FEEDBACK_COMMENT_SHOW_REPLIES, childrenCount);
                toggle.getElement().setAttribute("title", label);
                toggle.getElement().setAttribute("aria-label", label);
                toggle.setIcon((nowVisible ? VaadinIcon.CHEVRON_UP : VaadinIcon.CHEVRON_DOWN).create());
            });
            controls.add(toggle, toggleCount);
        }

        actions.add(reactions);
        if (reactions.getComponentCount() > 0 && controls.getComponentCount() > 0) {
            actions.add(divider);
        }
        actions.add(controls);
        return actions;
    }

    /** Recursively forces every nested .comment-children container visible -- expanding a node reveals its whole subtree at once, not just its direct children. */
    private static void forceExpandDescendants(Component root) {
        root.getChildren().forEach(child -> {
            if (child.hasClassName("comment-children")) {
                child.setVisible(true);
            }
            forceExpandDescendants(child);
        });
    }

    /** Recursively records every descendant comment's own id -- so a future reload() rebuild keeps the whole cascaded-open subtree expanded, not just the one node directly clicked. */
    private static void collectDescendantIds(Long commentId, Map<Long, List<FeedbackCommentDto>> byParent, Set<Long> out) {
        for (FeedbackCommentDto child : byParent.getOrDefault(commentId, List.of())) {
            out.add(child.id());
            collectDescendantIds(child.id(), byParent, out);
        }
    }

    private Div buildEditComposer(FeedbackCommentDto comment, Div textContainer, Div editFormSlot) {
        UiTextArea editField = new UiTextArea(getValue(FEEDBACK_COMMENT_FIELD_TEXT), null,
                FeedbackCommentSaveDto.TEXT_MAX_LENGTH, true, "comment-edit-field-text");
        String draftText = draftEditText.getOrDefault(comment.id(), comment.commentText());
        editField.setValue(draftText);
        editField.addValueChangeListener(e -> draftEditText.put(comment.id(), e.getValue()));

        UiIconButton saveButton = new UiIconButton(getValue(FEEDBACK_FORM_BUTTON_SUBMIT), VaadinIcon.CHECK.create());
        saveButton.addClassName("comment-save-icon");
        saveButton.addClickListener(_ -> {
            if (editField.getValue() == null || editField.getValue().isBlank()) return;
            feedbackAccessService.saveComment(new FeedbackCommentSaveDto(comment.id(), comment.feedbackId(),
                    comment.parentCommentId(), comment.authorId(), editField.getValue(), comment.version()));
            openEditComposerFor.remove(comment.id());
            draftEditText.remove(comment.id());
            reload();
        });

        UiIconButton discardButton = new UiIconButton(getValue(FEEDBACK_FORM_BUTTON_DISCARD), VaadinIcon.CLOSE_SMALL.create());
        discardButton.addClassName("comment-discard-icon");
        discardButton.addClickListener(_ -> {
            editField.setValue(comment.commentText());
            draftEditText.put(comment.id(), comment.commentText());
        });

        Div fields = new Div();
        fields.addClassName("comment-inline-form-fields");
        fields.add(editField);

        Div actionsCol = new Div();
        actionsCol.addClassName("comment-inline-form-actions");
        actionsCol.add(saveButton, discardButton);

        Div inlineForm = new Div();
        inlineForm.addClassName("comment-inline-form");
        inlineForm.add(fields, actionsCol);
        return inlineForm;
    }

    private Div buildReplyComposer(Long parentCommentId) {
        Div composer = new Div();
        composer.addClassName("comment-inline-form");
        if (!access.isLoggedIn()) return composer;

        Long draftKey = parentCommentId == null ? 0L : parentCommentId;
        UiTextArea replyField = new UiTextArea(getValue(FEEDBACK_COMMENT_FIELD_TEXT), null,
                FeedbackCommentSaveDto.TEXT_MAX_LENGTH, true, "comment-reply-field-text");
        String draftText = draftReplyText.getOrDefault(draftKey, "");
        replyField.setValue(draftText);
        replyField.addValueChangeListener(e -> draftReplyText.put(draftKey, e.getValue()));

        UiIconButton saveButton = new UiIconButton(getValue(FEEDBACK_FORM_BUTTON_SUBMIT), VaadinIcon.CHECK.create());
        saveButton.addClassName("comment-save-icon");
        saveButton.addClickListener(_ -> {
            if (replyField.getValue() == null || replyField.getValue().isBlank()) return;
            feedbackAccessService.saveComment(new FeedbackCommentSaveDto(null, feedbackId,
                    parentCommentId, access.getCurrentUserId(), replyField.getValue(), null));
            openReplyComposerFor.remove(draftKey);
            draftReplyText.remove(draftKey);
            expandedCommentIds.add(draftKey);
            reload();
        });

        UiIconButton closeButton = new UiIconButton(getValue(FEEDBACK_FORM_BUTTON_CLOSE), VaadinIcon.CLOSE.create());
        closeButton.addClassName("comment-close-icon");
        closeButton.addClickListener(_ -> preserveScroll(() -> {
            if (parentCommentId == null) {
                topLevelReplySlot.removeAll();
                topLevelReplySlot.add(buildTopLevelReplyTrigger());
            } else {
                Div replySlot = (Div) composer.getParent().get();
                replySlot.removeAll();
                replySlot.add(buildReplyTrigger(parentCommentId));
            }
            openReplyComposerFor.remove(draftKey);
            draftReplyText.remove(draftKey);
        }));

        Div fields = new Div();
        fields.addClassName("comment-inline-form-fields");
        fields.add(replyField);

        Div actionsCol = new Div();
        actionsCol.addClassName("comment-inline-form-actions");
        actionsCol.add(saveButton, closeButton);

        composer.add(fields, actionsCol);
        return composer;
    }

    private Div buildReactionButton(FeedbackCommentDto comment, FeedbackReactionType type, VaadinIcon icon) {
        List<Long> reactorIds = comment.reactorIdsByType().getOrDefault(type.name(), List.of());

        UiIconButton button = new UiIconButton(type.name(), icon.create());
        button.addClassName("comment-reaction-button");
        if (type.name().equals(comment.myReaction())) {
            button.addClassName("comment-reaction-button-active");
        }
        if (!reactorIds.isEmpty()) {
            String reactorNames = reactorIds.stream().map(id -> namesById.getOrDefault(id, "")).collect(Collectors.joining(", "));
            button.getElement().setAttribute("title", reactorNames);
        }
        button.setEnabled(access.isLoggedIn());
        button.addClickListener(_ -> {
            feedbackAccessService.saveReaction(new FeedbackCommentReactionSaveDto(comment.id(), access.getCurrentUserId(), type));
            reload();
        });

        Span count = new Span(String.valueOf(reactorIds.size()));
        count.addClassName("comment-reaction-count");

        Div wrapper = new Div();
        wrapper.addClassName("comment-reaction-wrapper");
        wrapper.add(button, count);
        return wrapper;
    }

    private void preserveScroll(Runnable action) {
        getElement().executeJs(
                "var sc = $0.closest('.overlay__content');" +
                "$0.__savedScrollTop = sc ? sc.scrollTop : null;",
                getElement());
        action.run();
        getElement().executeJs(
                "if ($0.__savedScrollTop != null) {" +
                "  var sc = $0.closest('.overlay__content');" +
                "  if (sc) {" +
                "    var target = $0.__savedScrollTop;" +
                "    var apply = function() { if (sc.scrollTop !== target) sc.scrollTop = target; };" +
                "    apply();" +
                "    var endTime = Date.now() + 500;" +
                "    var observer = new MutationObserver(function() { apply(); });" +
                "    observer.observe(sc, { childList: true, subtree: true, attributes: true });" +
                "    var tick = function() {" +
                "      apply();" +
                "      if (Date.now() < endTime) { requestAnimationFrame(tick); } else { observer.disconnect(); }" +
                "    };" +
                "    requestAnimationFrame(tick);" +
                "  }" +
                "}",
                getElement());
    }

    private void reload() {
        preserveScroll(() -> configure(new Parameters(feedbackId)));
    }

    public void toggleTopLevelComments() {
        if (topLevelCommentsContainer == null) return;
        boolean nowVisible = !topLevelCommentsContainer.isVisible();
        topLevelCommentsContainer.setVisible(nowVisible);
        if (nowVisible) {
            expandedCommentIds.add(0L);
            forceExpandDescendants(topLevelCommentsContainer);
        } else {
            expandedCommentIds.remove(0L);
        }
    }

    public boolean isTopLevelCommentsExpanded() {
        return expandedCommentIds.contains(0L);
    }

    public int getTopLevelCommentCount() {
        return topLevelCommentCount;
    }

    public void setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged;
    }
}
