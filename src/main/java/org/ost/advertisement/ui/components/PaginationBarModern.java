package org.ost.advertisement.ui.components;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import java.util.function.Consumer;
import lombok.Getter;
import lombok.Setter;

public class PaginationBarModern extends HorizontalLayout {

	private final Button firstButton = new Button("« First");
	private final Button prevButton = new Button("‹ Prev");
	private final Button nextButton = new Button("Next ›");
	private final Button lastButton = new Button("Last »");
	private final Span pageIndicator = new Span();

	@Getter
	private int currentPage = 0;
	@Getter
	private int pageSize = 25;
	private int totalCount = 0;

	@Setter
	private Consumer<PaginationEvent> pageChangeListener;

	public PaginationBarModern() {
		setAlignItems(Alignment.CENTER);
		setSpacing(true);

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

	public void setPageSize(int size) {
		this.pageSize = size;
		updateUI();
	}

	private int getTotalPages() {
		return Math.max(1, (int) Math.ceil((double) totalCount / pageSize));
	}

	private void updateUI() {
		int totalPages = getTotalPages();
		pageIndicator.setText("Page " + (currentPage + 1) + " of " + totalPages);

		firstButton.setEnabled(currentPage > 0);
		prevButton.setEnabled(currentPage > 0);
		nextButton.setEnabled(currentPage < totalPages - 1);
		lastButton.setEnabled(currentPage < totalPages - 1);
	}

	private void triggerCallback() {
		updateUI();
		if (pageChangeListener != null) {
			pageChangeListener.accept(new PaginationEvent(currentPage, pageSize));
		}
	}

	public record PaginationEvent(int page, int size) {}
}
