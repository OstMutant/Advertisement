package org.ost.marketplace.ui.views.components;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
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
import org.ost.marketplace.ui.views.components.fields.UiTextArea;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.orchestrator.services.FeedbackAccessService;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.feedback.dto.FeedbackAggregateDto;
import org.ost.platform.feedback.dto.FeedbackDto;
import org.ost.platform.feedback.dto.FeedbackSaveDto;
import org.springframework.context.annotation.Scope;

import java.util.List;

import static org.ost.marketplace.services.i18n.I18nKey.*;

/** Rating+text feedback list and submission form for one owning entity -- hidden entirely when {@code feedback-spring-boot-starter} is absent. */
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class FeedbackPanel extends Div
        implements Configurable<FeedbackPanel, FeedbackPanel.Parameters>, Initialization<FeedbackPanel>, I18nParams {

    private static final int PAGE_SIZE = 20;

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull EntityRef entityRef;
    }

    private final FeedbackAccessService feedbackAccessService;
    private final AccessEvaluator       access;
    private final NotificationService   notificationService;
    @Getter
    private final I18nService           i18nService;

    private Div headerContainer;
    private Div listContainer;

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
        refresh(entityRef);

        if (access.isLoggedIn()) {
            add(buildForm(entityRef));
        }
        return this;
    }

    private void refresh(EntityRef entityRef) {
        refreshHeader(entityRef);
        refreshList(entityRef);
    }

    private void refreshHeader(EntityRef entityRef) {
        headerContainer.removeAll();
        FeedbackAggregateDto aggregate = feedbackAccessService.getAggregate(entityRef.entityType(), entityRef.entityId());
        Span label = new Span(getValue(FEEDBACK_SECTION_LABEL));
        label.addClassName("feedback-header-label");
        headerContainer.add(label);
        if (aggregate.reviewCount() > 0) {
            Span count = new Span(getValue(FEEDBACK_AGGREGATE_COUNT, aggregate.reviewCount(), "%.1f".formatted(aggregate.avgRating())));
            count.addClassName("feedback-header-count");
            headerContainer.add(count);
        }
    }

    private void refreshList(EntityRef entityRef) {
        listContainer.removeAll();
        List<FeedbackDto> entries = feedbackAccessService.findForEntity(entityRef.entityType(), entityRef.entityId(), 0, PAGE_SIZE);
        if (entries.isEmpty()) {
            Span empty = new Span(getValue(FEEDBACK_EMPTY));
            empty.addClassName("feedback-empty");
            listContainer.add(empty);
            return;
        }
        entries.forEach(entry -> listContainer.add(buildEntryRow(entry)));
    }

    private static Div buildEntryRow(FeedbackDto entry) {
        Div row = new Div();
        row.addClassName("feedback-entry");

        Span stars = new Span("★".repeat(entry.rating()) + "☆".repeat(5 - entry.rating()));
        stars.addClassName("feedback-entry-rating");

        Div text = new Div();
        text.addClassName("feedback-entry-text");
        text.getElement().setProperty("innerHTML", entry.feedbackText());

        row.add(stars, text);
        return row;
    }

    private Div buildForm(EntityRef entityRef) {
        Div form = new Div();
        form.addClassName("feedback-form");

        RadioButtonGroup<Integer> ratingField = new RadioButtonGroup<>();
        ratingField.setLabel(getValue(FEEDBACK_FORM_FIELD_RATING));
        ratingField.setItems(1, 2, 3, 4, 5);
        ratingField.setItemLabelGenerator(n -> "★".repeat(n));
        ratingField.setValue(5);
        ratingField.addClassName("feedback-form-rating");

        UiTextArea textField = new UiTextArea(getValue(FEEDBACK_FORM_FIELD_TEXT), null, FeedbackSaveDto.TEXT_MAX_LENGTH, true, "feedback-form-field-text");

        Button submit = new Button(getValue(FEEDBACK_FORM_BUTTON_SUBMIT));
        submit.addClassName("feedback-form-submit");
        submit.addClickListener(_ -> {
            if (ratingField.getValue() == null || textField.getValue() == null || textField.getValue().isBlank()) return;
            feedbackAccessService.save(new FeedbackSaveDto(null, entityRef.entityType(), entityRef.entityId(),
                    access.getCurrentUserId(), ratingField.getValue(), textField.getValue(), null));
            textField.clear();
            ratingField.setValue(5);
            notificationService.success(FEEDBACK_NOTIFICATION_SAVED);
            refresh(entityRef);
        });

        form.add(ratingField, textField, submit);
        return form;
    }
}
