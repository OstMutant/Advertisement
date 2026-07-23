package org.ost.marketplace.ui.views.components;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Getter;
import lombok.Setter;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.springframework.context.annotation.Scope;

import java.util.function.Consumer;

import org.ost.marketplace.ui.core.PaginationDefaults;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@SpringComponent
@Scope("prototype")
public class PaginationBar extends HorizontalLayout implements I18nParams {

    @Getter
    private int pageSize = PaginationDefaults.DEFAULT_PAGE_SIZE;

    @Getter
    private final transient I18nService i18nService;

    private final UiIconButton firstButton;
    private final UiIconButton prevButton;
    private final UiIconButton nextButton;
    private final UiIconButton lastButton;
    private final Span pageIndicator = new Span();
    private final Span resultCount   = new Span();

    @Getter
    private int currentPage = 0;
    private int totalCount = 0;

    @Setter
    private transient Consumer<PaginationEvent> pageChangeListener;

    public PaginationBar(I18nService i18nService) {
        this.i18nService = i18nService;
        setAlignItems(Alignment.CENTER);
        setSpacing(true);

        firstButton = new UiIconButton(i18nService.get(PAGINATION_FIRST), VaadinIcon.ANGLE_DOUBLE_LEFT.create());
        prevButton = new UiIconButton(i18nService.get(PAGINATION_PREV), VaadinIcon.ANGLE_LEFT.create());
        nextButton = new UiIconButton(i18nService.get(PAGINATION_NEXT), VaadinIcon.ANGLE_RIGHT.create());
        lastButton = new UiIconButton(i18nService.get(PAGINATION_LAST), VaadinIcon.ANGLE_DOUBLE_RIGHT.create());

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

        resultCount.addClassName("pagination-count");
        add(firstButton, prevButton, pageIndicator, nextButton, lastButton, resultCount);
        updateUI();
    }

    public void setPageSize(int size) {
        this.pageSize = size;
        this.currentPage = 0;
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

        int from = totalCount == 0 ? 0 : currentPage * pageSize + 1;
        int to   = Math.min((currentPage + 1) * pageSize, totalCount);
        resultCount.setText(getValue(PAGINATION_COUNT, from, to, totalCount));

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
