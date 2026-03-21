package org.ost.advertisement.ui.views.components;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Getter;
import lombok.Setter;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.rules.I18nParams;
import org.springframework.context.annotation.Scope;

import java.util.function.Consumer;

import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@Scope("prototype")
public class PaginationBarModern extends HorizontalLayout implements I18nParams {

    @Getter
    private final int pageSize = 25;

    @Getter
    private final transient I18nService i18nService;

    private final Button firstButton;
    private final Button prevButton;
    private final Button nextButton;
    private final Button lastButton;
    private final Span pageIndicator = new Span();

    @Getter
    private int currentPage = 0;
    private int totalCount = 0;

    @Setter
    private transient Consumer<PaginationEvent> pageChangeListener;

    public PaginationBarModern(I18nService i18nService) {
        this.i18nService = i18nService;
        setAlignItems(Alignment.CENTER);
        setSpacing(true);

        firstButton = new Button(getValue(PAGINATION_FIRST));
        prevButton  = new Button(getValue(PAGINATION_PREV));
        nextButton  = new Button(getValue(PAGINATION_NEXT));
        lastButton  = new Button(getValue(PAGINATION_LAST));

        firstButton.addClickListener(_ -> {
            currentPage = 0;
            triggerCallback();
        });
        prevButton.addClickListener(_ -> {
            if (currentPage > 0) {
                currentPage--;
                triggerCallback();
            }
        });
        nextButton.addClickListener(_ -> {
            if (currentPage < getTotalPages() - 1) {
                currentPage++;
                triggerCallback();
            }
        });
        lastButton.addClickListener(_ -> {
            currentPage = getTotalPages() - 1;
            triggerCallback();
        });

        firstButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        prevButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        nextButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        lastButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        add(firstButton, prevButton, pageIndicator, nextButton, lastButton);
        updateUI();
    }

    public void setTotalCount(int total) {
        this.totalCount = total;
        if (currentPage >= getTotalPages()) {
            currentPage = Math.max(0, getTotalPages() - 1);
        }
        updateUI();
    }

    private int getTotalPages() {
        return Math.max(1, (int) Math.ceil((double) totalCount / pageSize));
    }

    private void updateUI() {
        int totalPages = getTotalPages();
        pageIndicator.setText(getValue(PAGINATION_INDICATOR, currentPage + 1, totalPages));

        firstButton.setEnabled(currentPage > 0);
        prevButton.setEnabled(currentPage > 0);
        nextButton.setEnabled(currentPage < totalPages - 1);
        lastButton.setEnabled(currentPage < totalPages - 1);
    }

    private void triggerCallback() {
        if (pageChangeListener != null) {
            pageChangeListener.accept(new PaginationEvent(currentPage, pageSize));
        }
    }

    public record PaginationEvent(int page, int size) {

    }
}

