package org.ost.marketplace.ui.views.components;

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
import org.ost.marketplace.ui.views.components.dialogs.ReportReasonDialog;
import org.ost.marketplace.ui.views.components.fields.UiTextArea;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.ost.orchestrator.services.FeedbackAccessService;
import org.ost.orchestrator.services.FeedbackCommentWriteService;
import org.ost.platform.feedback.dto.FeedbackCommentDto;
import org.ost.platform.feedback.dto.FeedbackCommentSaveDto;
import org.ost.platform.feedback.model.FeedbackModerationStatus;
import org.springframework.context.annotation.Scope;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_BUTTON_REPORT;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_COMMENT_BUTTON_REPLY;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_COMMENT_CENSORED_TEXT;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_COMMENT_CONFIRM_CANCEL_BUTTON;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_COMMENT_CONFIRM_DELETE_BUTTON;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_COMMENT_CONFIRM_DELETE_TEXT;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_COMMENT_CONFIRM_DELETE_TITLE;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_COMMENT_FIELD_TEXT;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_CONFIRM_REPORT_BUTTON;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_CONFIRM_REPORT_CANCEL_BUTTON;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_CONFIRM_REPORT_TITLE;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_FORM_BUTTON_DISCARD;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_FORM_BUTTON_EDIT;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_FORM_BUTTON_SUBMIT;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_REPORT_REASON_LABEL;
import static org.ost.marketplace.services.i18n.I18nKey.USER_VIEW_BUTTON_DELETE;

/** The single reply to a feedback entry -- written only by the reviewed entity's owner, edited/deleted only by that same author within the edit window, reportable by any other viewer. */
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class FeedbackReplyPanel extends Div
        implements Configurable<FeedbackReplyPanel, FeedbackReplyPanel.Parameters>, Initialization<FeedbackReplyPanel>, I18nParams {

    private static final Duration EDIT_WINDOW = Duration.ofHours(48);

    public record Parameters(@NonNull Long feedbackId, @NonNull Long ownerUserId) {
    }

    private final FeedbackAccessService       feedbackAccessService;
    private final FeedbackCommentWriteService feedbackCommentWriteService;
    private final AccessEvaluator             access;
    @Getter
    private final I18nService                 i18nService;

    private Long feedbackId;
    private Long ownerUserId;
    private boolean composerOpen = false;
    private String draftText = "";

    @Override
    @PostConstruct
    public FeedbackReplyPanel init() {
        addClassName("feedback-reply-panel");
        return this;
    }

    @Override
    public FeedbackReplyPanel configure(Parameters p) {
        removeAll();
        feedbackId = p.feedbackId();
        ownerUserId = p.ownerUserId();

        Optional<FeedbackCommentDto> comment = feedbackAccessService.findCommentByFeedback(feedbackId);
        if (comment.isPresent()) {
            add(buildReplyNode(comment.get()));
        } else if (access.isStrictOwner(ownerUserId)) {
            add(composerOpen ? buildComposer(null) : buildReplyTrigger());
        }
        return this;
    }

    private Div buildReplyTrigger() {
        Div trigger = new Div();
        trigger.addClassName("feedback-reply-trigger");
        UiIconButton triggerButton = new UiIconButton(getValue(FEEDBACK_COMMENT_BUTTON_REPLY), VaadinIcon.REPLY.create());
        triggerButton.addClassName("feedback-reply-icon");
        triggerButton.addClickListener(_ -> {
            composerOpen = true;
            reload();
        });
        trigger.add(triggerButton);
        return trigger;
    }

    private Div buildComposer(FeedbackCommentDto existing) {
        Div composer = new Div();
        composer.addClassName("feedback-reply-composer");

        UiTextArea textField = new UiTextArea(getValue(FEEDBACK_COMMENT_FIELD_TEXT), null,
                FeedbackCommentSaveDto.TEXT_MAX_LENGTH, true, "feedback-reply-field-text");
        textField.setValue(existing != null ? existing.commentText() : draftText);
        textField.addValueChangeListener(e -> draftText = e.getValue());

        UiIconButton saveButton = new UiIconButton(getValue(FEEDBACK_FORM_BUTTON_SUBMIT), VaadinIcon.CHECK.create());
        saveButton.addClassName("feedback-reply-save-icon");
        saveButton.addClickListener(_ -> {
            if (textField.getValue() == null || textField.getValue().isBlank()) return;
            Long id = existing != null ? existing.id() : null;
            Long version = existing != null ? existing.version() : null;
            Long authorId = existing != null ? existing.authorId() : access.getCurrentUserId();
            feedbackCommentWriteService.saveComment(new FeedbackCommentSaveDto(id, feedbackId, authorId, textField.getValue(), version));
            composerOpen = false;
            draftText = "";
            reload();
        });

        UiIconButton cancelButton = new UiIconButton(getValue(FEEDBACK_FORM_BUTTON_DISCARD), VaadinIcon.CLOSE_SMALL.create());
        cancelButton.addClassName("feedback-reply-discard-icon");
        cancelButton.addClickListener(_ -> {
            if (existing != null && !Objects.equals(textField.getValue(), existing.commentText())) {
                textField.setValue(existing.commentText());
                draftText = existing.commentText();
            } else {
                composerOpen = false;
                draftText = "";
                reload();
            }
        });

        Div actions = new Div();
        actions.addClassName("feedback-reply-composer-actions");
        actions.add(saveButton, cancelButton);

        composer.add(textField, actions);
        return composer;
    }

    private Div buildReplyNode(FeedbackCommentDto comment) {
        Div node = new Div();
        node.addClassName("feedback-reply-node");

        if (composerOpen) {
            node.add(buildComposer(comment));
            return node;
        }

        Div textContainer = new Div();
        textContainer.addClassName("feedback-reply-text-container");
        if (comment.moderationStatus() == FeedbackModerationStatus.HIDDEN) {
            Span censored = new Span(getValue(FEEDBACK_COMMENT_CENSORED_TEXT));
            censored.addClassName("feedback-reply-censored-text");
            textContainer.add(censored);
        } else {
            Div text = new Div();
            text.addClassName("feedback-reply-text");
            text.getElement().setProperty("innerHTML", comment.commentText());
            textContainer.add(text);
        }

        boolean isAuthor = access.isStrictOwner(ownerUserId)
                && Instant.now().isBefore(comment.createdAt().plus(EDIT_WINDOW));
        boolean canReport = access.isLoggedIn() && !comment.authorId().equals(access.getCurrentUserId());

        Div actions = new Div();
        actions.addClassName("feedback-reply-actions");

        if (isAuthor) {
            UiIconButton editButton = new UiIconButton(getValue(FEEDBACK_FORM_BUTTON_EDIT), VaadinIcon.EDIT.create());
            editButton.addClassName("feedback-reply-edit-icon");
            editButton.addClickListener(_ -> {
                composerOpen = true;
                reload();
            });
            actions.add(editButton);

            UiIconButton deleteButton = new UiIconButton(getValue(USER_VIEW_BUTTON_DELETE), VaadinIcon.TRASH.create());
            deleteButton.addClassName("feedback-reply-delete-icon");
            deleteButton.addClickListener(_ -> new ConfirmActionDialog(
                    getValue(FEEDBACK_COMMENT_CONFIRM_DELETE_TITLE),
                    getValue(FEEDBACK_COMMENT_CONFIRM_DELETE_TEXT),
                    getValue(FEEDBACK_COMMENT_CONFIRM_DELETE_BUTTON),
                    getValue(FEEDBACK_COMMENT_CONFIRM_CANCEL_BUTTON),
                    () -> {
                        feedbackCommentWriteService.deleteComment(comment.id(), access.getCurrentUserId());
                        reload();
                    }
            ).open());
            actions.add(deleteButton);
        }

        if (canReport) {
            UiIconButton reportButton = new UiIconButton(getValue(FEEDBACK_BUTTON_REPORT), VaadinIcon.FLAG.create());
            reportButton.addClassName("feedback-reply-report-icon");
            reportButton.setEnabled(comment.moderationStatus() == FeedbackModerationStatus.NEW);
            reportButton.addClickListener(_ -> new ReportReasonDialog(
                    getValue(FEEDBACK_CONFIRM_REPORT_TITLE),
                    getValue(FEEDBACK_REPORT_REASON_LABEL),
                    getValue(FEEDBACK_CONFIRM_REPORT_BUTTON),
                    getValue(FEEDBACK_CONFIRM_REPORT_CANCEL_BUTTON),
                    reason -> {
                        feedbackAccessService.flagComment(comment.id(), access.getCurrentUserId(), reason);
                        reload();
                    }
            ).open());
            actions.add(reportButton);
        }

        node.add(textContainer, actions);
        return node;
    }

    private void reload() {
        configure(new Parameters(feedbackId, ownerUserId));
    }
}
