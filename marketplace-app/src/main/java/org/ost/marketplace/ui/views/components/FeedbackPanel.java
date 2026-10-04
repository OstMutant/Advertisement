package org.ost.marketplace.ui.views.components;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.core.Configurable;
import org.ost.marketplace.ui.core.Initialization;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.components.fields.StarRatingField;
import org.ost.marketplace.ui.views.components.fields.UiTextArea;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.orchestrator.services.FeedbackAccessService;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.feedback.dto.FeedbackAggregateDto;
import org.ost.platform.feedback.dto.FeedbackDto;
import org.ost.platform.feedback.dto.FeedbackSaveDto;
import org.springframework.context.annotation.Scope;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.ost.marketplace.services.i18n.I18nKey.*;

/** Rating+text feedback list and submission form for one owning entity -- hidden entirely when {@code feedback-spring-boot-starter} is absent. */
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class FeedbackPanel extends Div
        implements Configurable<FeedbackPanel, FeedbackPanel.Parameters>, Initialization<FeedbackPanel>, I18nParams {

    private static final int      PAGE_SIZE   = 20;
    private static final Duration EDIT_WINDOW = Duration.ofHours(48);

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull EntityRef entityRef;
    }

    private final FeedbackAccessService feedbackAccessService;
    private final AccessEvaluator       access;
    private final NotificationService   notificationService;
    private final UiComponentFactory<CommentTreePanel, CommentTreePanel.Parameters> commentTreePanelFactory;
    @Getter
    private final I18nService           i18nService;

    private Div headerContainer;
    private Div listContainer;
    private Map<Long, String> authorNamesById = Map.of();
    private boolean hasOwnEntry;
    private Div formContainer;
    private boolean addFormOpen = false;

    @Override
    @PostConstruct
    public FeedbackPanel init() {
        addClassName("feedback-panel");
        return this;
    }

    @Override
    public FeedbackPanel configure(Parameters p) {
        removeAll();
        boolean available = feedbackAccessService.isAvailable();
        setVisible(available);
        if (!available) return this;

        EntityRef entityRef = p.getEntityRef();

        headerContainer = new Div();
        headerContainer.addClassName("feedback-header");
        add(headerContainer);
        listContainer = new Div();
        listContainer.addClassName("feedback-list");
        add(listContainer);
        buildFormContainer(entityRef);
        add(formContainer);
        refresh(entityRef);
        return this;
    }

    private void buildFormContainer(EntityRef entityRef) {
        formContainer = new Div();
        formContainer.addClassName("feedback-form-container");
        if (addFormOpen && access.isLoggedIn() && !hasOwnEntry) {
            formContainer.add(buildForm(entityRef));
        } else {
            formContainer.add(buildAddTrigger(entityRef));
        }
    }

    private Div buildAddTrigger(EntityRef entityRef) {
        Div trigger = new Div();
        trigger.addClassName("feedback-add-trigger");
        UiIconButton triggerButton = new UiIconButton(getValue(FEEDBACK_FORM_BUTTON_ADD), VaadinIcon.PLUS.create());
        triggerButton.addClassName("feedback-add-button");
        triggerButton.addClickListener(_ -> {
            addFormOpen = true;
            formContainer.removeAll();
            formContainer.add(buildForm(entityRef));
        });
        trigger.add(triggerButton);
        return trigger;
    }

    private void updateAddControlsVisibility(EntityRef entityRef) {
        if (access.isLoggedIn() && !hasOwnEntry) {
            if (!addFormOpen) {
                formContainer.removeAll();
                formContainer.add(buildAddTrigger(entityRef));
            }
        } else {
            formContainer.removeAll();
            addFormOpen = false;
        }
    }

    private void refresh(EntityRef entityRef) {
        refreshHeader(entityRef);
        refreshList(entityRef);
        updateAddControlsVisibility(entityRef);
    }

    private void refreshHeader(EntityRef entityRef) {
        headerContainer.removeAll();
        FeedbackAggregateDto aggregate = feedbackAccessService.getAggregate(entityRef.entityType(), entityRef.entityId());
        Span label = new Span(getValue(FEEDBACK_SECTION_LABEL));
        label.addClassName("feedback-header-label");
        headerContainer.add(label);
        if (aggregate.reviewCount() > 0) {
            int rounded = (int) Math.round(aggregate.avgRating());
            Span stars = new Span("★".repeat(rounded) + "☆".repeat(5 - rounded));
            stars.addClassName("feedback-header-stars");
            Span avg = new Span("%.1f".formatted(aggregate.avgRating()));
            avg.addClassName("feedback-header-avg");
            Span count = new Span(getValue(FEEDBACK_AGGREGATE_COUNT, aggregate.reviewCount()));
            count.addClassName("feedback-header-count");
            headerContainer.add(stars, avg, count);
        }
    }

    private void refreshList(EntityRef entityRef) {
        listContainer.removeAll();
        List<FeedbackDto> entries = feedbackAccessService.findForEntity(entityRef.entityType(), entityRef.entityId(), 0, PAGE_SIZE);
        hasOwnEntry = access.isLoggedIn()
                && entries.stream().anyMatch(entry -> entry.authorId().equals(access.getCurrentUserId()));
        if (entries.isEmpty()) {
            Span empty = new Span(getValue(FEEDBACK_EMPTY));
            empty.addClassName("feedback-empty");
            listContainer.add(empty);
            return;
        }
        authorNamesById = feedbackAccessService.resolveAuthorNames(
                entries.stream().map(FeedbackDto::authorId).collect(Collectors.toSet()));
        entries.forEach(entry -> {
            CommentTreePanel commentTreePanel = commentTreePanelFactory.build(new CommentTreePanel.Parameters(entry.id()));
            listContainer.add(buildEntryRow(entityRef, entry, commentTreePanel));
            listContainer.add(commentTreePanel);
        });
    }

    private Div buildEntryRow(EntityRef entityRef, FeedbackDto entry, CommentTreePanel commentTreePanel) {
        Div row = new Div();
        row.addClassName("feedback-entry");

        boolean editable = access.isLoggedIn()
                && entry.authorId().equals(access.getCurrentUserId())
                && Instant.now().isBefore(entry.createdAt().plus(EDIT_WINDOW));

        Div viewContainer = new Div();
        Div editContainer = new Div();
        editContainer.addClassName("feedback-entry-edit");
        editContainer.setVisible(false);

        Span stars = new Span("★".repeat(entry.rating()) + "☆".repeat(5 - entry.rating()));
        stars.addClassName("feedback-entry-rating");

        Div text = new Div();
        text.addClassName("feedback-entry-text");
        text.getElement().setProperty("innerHTML", entry.feedbackText());

        viewContainer.add(stars, text);

        Div header = new Div();
        header.addClassName("feedback-entry-header");
        Span author = new Span(authorNamesById.getOrDefault(entry.authorId(), ""));
        author.addClassName("feedback-entry-author");
        header.add(author);

        Div headerActions = new Div();
        headerActions.addClassName("feedback-entry-header-actions");

        if (editable) {
            StarRatingField editRatingField = new StarRatingField();
            editRatingField.setValue(entry.rating());
            editRatingField.addClassName("feedback-entry-edit-rating");

            UiTextArea editTextField = new UiTextArea(getValue(FEEDBACK_FORM_FIELD_TEXT), null,
                    FeedbackSaveDto.TEXT_MAX_LENGTH, true, "feedback-entry-edit-field-text");
            editTextField.setValue(entry.feedbackText());

            UiIconButton saveButton = new UiIconButton(getValue(FEEDBACK_FORM_BUTTON_SUBMIT), VaadinIcon.CHECK.create());
            saveButton.addClassName("feedback-entry-save-icon");
            saveButton.addClickListener(_ -> {
                if (editRatingField.getValue() == null || editTextField.getValue() == null || editTextField.getValue().isBlank()) return;
                feedbackAccessService.save(new FeedbackSaveDto(entry.id(), entityRef.entityType(), entityRef.entityId(),
                        access.getCurrentUserId(), editRatingField.getValue(), editTextField.getValue(), entry.version()));
                notificationService.success(FEEDBACK_NOTIFICATION_SAVED);
                refresh(entityRef);
            });

            UiIconButton discardButton = new UiIconButton(getValue(FEEDBACK_FORM_BUTTON_DISCARD), VaadinIcon.CLOSE_SMALL.create());
            discardButton.addClassName("feedback-entry-discard-icon");
            discardButton.addClickListener(_ -> {
                editRatingField.setValue(entry.rating());
                editTextField.setValue(entry.feedbackText());
            });

            Div editFields = new Div();
            editFields.addClassName("feedback-entry-edit-fields");
            editFields.add(editRatingField, editTextField);

            Div editActions = new Div();
            editActions.addClassName("feedback-entry-edit-actions");
            editActions.add(saveButton, discardButton);

            editContainer.add(editFields, editActions);

            UiIconButton editButton = new UiIconButton(getValue(FEEDBACK_FORM_BUTTON_EDIT), VaadinIcon.EDIT.create());
            editButton.addClassName("feedback-entry-edit-icon");
            editButton.addClickListener(_ -> {
                boolean opening = !editContainer.isVisible();
                if (opening) {
                    viewContainer.setVisible(false);
                    editContainer.setVisible(true);
                } else {
                    editRatingField.setValue(entry.rating());
                    editTextField.setValue(entry.feedbackText());
                    viewContainer.setVisible(true);
                    editContainer.setVisible(false);
                }
            });
            headerActions.add(editButton);
        }

        refreshCommentToggle(headerActions, commentTreePanel);
        commentTreePanel.setOnChanged(() -> refreshCommentToggle(headerActions, commentTreePanel));

        header.add(headerActions);
        row.add(header);
        row.add(viewContainer);
        row.add(editContainer);
        return row;
    }

    private void refreshCommentToggle(Div headerActions, CommentTreePanel commentTreePanel) {
        headerActions.getChildren()
                .filter(c -> c.hasClassName("comment-toggle-icon"))
                .toList()
                .forEach(headerActions::remove);
        if (commentTreePanel.getTopLevelCommentCount() > 0) {
            boolean expanded = commentTreePanel.isTopLevelCommentsExpanded();
            UiIconButton toggleButton = new UiIconButton(
                    getValue(expanded ? FEEDBACK_COMMENT_HIDE_REPLIES : FEEDBACK_COMMENT_SHOW_REPLIES, commentTreePanel.getTopLevelCommentCount()),
                    (expanded ? VaadinIcon.CHEVRON_UP : VaadinIcon.CHEVRON_DOWN).create());
            toggleButton.addClassName("comment-toggle-icon");
            toggleButton.addClickListener(_ -> {
                commentTreePanel.toggleTopLevelComments();
                boolean nowExpanded = commentTreePanel.isTopLevelCommentsExpanded();
                String label = getValue(nowExpanded ? FEEDBACK_COMMENT_HIDE_REPLIES : FEEDBACK_COMMENT_SHOW_REPLIES, commentTreePanel.getTopLevelCommentCount());
                toggleButton.getElement().setAttribute("title", label);
                toggleButton.getElement().setAttribute("aria-label", label);
                toggleButton.setIcon((nowExpanded ? VaadinIcon.CHEVRON_UP : VaadinIcon.CHEVRON_DOWN).create());
            });
            headerActions.add(toggleButton);
        }
    }

    private Div buildForm(EntityRef entityRef) {
        Div form = new Div();
        form.addClassName("feedback-form");

        Span ratingLabel = new Span(getValue(FEEDBACK_FORM_FIELD_RATING));
        ratingLabel.addClassName("feedback-form-rating-label");

        StarRatingField ratingField = new StarRatingField();
        ratingField.setValue(5);
        ratingField.addClassName("feedback-form-rating");

        UiTextArea textField = new UiTextArea(getValue(FEEDBACK_FORM_FIELD_TEXT), null, FeedbackSaveDto.TEXT_MAX_LENGTH, true, "feedback-form-field-text");

        UiIconButton submit = new UiIconButton(getValue(FEEDBACK_FORM_BUTTON_SUBMIT), VaadinIcon.CHECK.create());
        submit.addClassName("feedback-form-submit");
        submit.addClickListener(_ -> {
            if (ratingField.getValue() == null || textField.getValue() == null || textField.getValue().isBlank()) return;
            feedbackAccessService.save(new FeedbackSaveDto(null, entityRef.entityType(), entityRef.entityId(),
                    access.getCurrentUserId(), ratingField.getValue(), textField.getValue(), null));
            notificationService.success(FEEDBACK_NOTIFICATION_SAVED);
            addFormOpen = false;
            refresh(entityRef);
        });

        UiIconButton close = new UiIconButton(getValue(FEEDBACK_FORM_BUTTON_CLOSE), VaadinIcon.CLOSE.create());
        close.addClassName("feedback-form-close");
        close.addClickListener(_ -> {
            addFormOpen = false;
            formContainer.removeAll();
            formContainer.add(buildAddTrigger(entityRef));
        });

        Div fields = new Div();
        fields.addClassName("feedback-form-fields");
        fields.add(ratingLabel, ratingField, textField);

        Div actions = new Div();
        actions.addClassName("feedback-form-actions");
        actions.add(submit, close);

        form.add(fields, actions);
        return form;
    }
}
