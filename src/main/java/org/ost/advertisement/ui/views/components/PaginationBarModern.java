package org.ost.advertisement.ui.views.components;

import static org.ost.advertisement.constants.I18nKey.PAGINATION_FIRST;
import static org.ost.advertisement.constants.I18nKey.PAGINATION_INDICATOR;
import static org.ost.advertisement.constants.I18nKey.PAGINATION_LAST;
import static org.ost.advertisement.constants.I18nKey.PAGINATION_NEXT;
import static org.ost.advertisement.constants.I18nKey.PAGINATION_PREV;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.Setter;
import org.ost.advertisement.services.I18nService;

public class PaginationBarModern extends HorizontalLayout {

	private final Button firstButton;
	private final Button prevButton;
	private final Button nextButton;
	private final Button lastButton;
	private final Span pageIndicator = new Span();

	@Getter
	private int currentPage = 0;
	@Getter
	private final int pageSize = 25;
	private int totalCount = 0;

	@Setter
	private Consumer<PaginationEvent> pageChangeListener;

	private final I18nService i18n;

	public PaginationBarModern(I18nService i18n) {
		this.i18n = i18n;
		setAlignItems(Alignment.CENTER);
		setSpacing(true);

		firstButton = new Button(i18n.get(PAGINATION_FIRST));
		prevButton = new Button(i18n.get(PAGINATION_PREV));
		nextButton = new Button(i18n.get(PAGINATION_NEXT));
		lastButton = new Button(i18n.get(PAGINATION_LAST));

		firstButton.addClickListener(e -> {
			currentPage = 0;
			triggerCallback();
		});
		prevButton.addClickListener(e -> {
			if (currentPage > 0) {
				currentPage--;
				triggerCallback();
			}
		});
		nextButton.addClickListener(e -> {
			if (currentPage < getTotalPages() - 1) {
				currentPage++;
				triggerCallback();
			}
		});
		lastButton.addClickListener(e -> {
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
		pageIndicator.setText(i18n.get(PAGINATION_INDICATOR, currentPage + 1, totalPages));

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

