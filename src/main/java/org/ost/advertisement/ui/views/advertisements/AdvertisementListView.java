package org.ost.advertisement.ui.views.advertisements;

import static org.ost.advertisement.ui.utils.TimeZoneUtil.formatInstant;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.Grid.Column;
import com.vaadin.flow.component.grid.HeaderRow;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.List;
import org.ost.advertisement.dto.AdvertisementFilter;
import org.ost.advertisement.entyties.Advertisement;
import org.ost.advertisement.repository.AdvertisementRepository;
import org.ost.advertisement.ui.components.PaginationBarModern;
import org.ost.advertisement.ui.components.SortToggleButton;
import org.ost.advertisement.ui.views.sort.CustomSort;
import org.springframework.data.domain.PageRequest;

@PageTitle("Advertisements | Advertisement App")
@Route("advertisements")
public class AdvertisementListView extends VerticalLayout {

	private final AdvertisementRepository repository;
	private final Grid<Advertisement> grid = new Grid<>(Advertisement.class, false);
	private final PaginationBarModern paginationBar = new PaginationBarModern();
	private final AdvertisementFilterFields filterFields = new AdvertisementFilterFields();
	private final CustomSort customSort = new CustomSort();

	public AdvertisementListView(AdvertisementRepository repository) {
		this.repository = repository;
		setSizeFull();

		paginationBar.setPageSize(25);
		paginationBar.setPageChangeListener(e -> refreshGrid());

		filterFields.configure(() -> {
			paginationBar.setTotalCount(0);
			refreshGrid();
		});

		configureGrid();
		add(grid, paginationBar);
		refreshGrid();
	}

	private void refreshGrid() {
		int page = paginationBar.getCurrentPage();
		int size = paginationBar.getPageSize();
		PageRequest pageable = PageRequest.of(page, size, customSort.getSort());
		AdvertisementFilter currentFilter = filterFields.getNewFilter();
		List<Advertisement> pageData = repository.findByFilter(currentFilter, pageable);
		int totalCount = repository.countByFilter(currentFilter).intValue();

		paginationBar.setTotalCount(totalCount);
		grid.setItems(pageData);
	}

	private void configureGrid() {
		grid.setSizeFull();

		Column<Advertisement> idColumn = grid.addColumn(Advertisement::getId)
			.setTextAlign(ColumnTextAlign.END)
			.setAutoWidth(true).setFlexGrow(0)
			.setHeader(createSortableHeader("ID", "id"));

		Column<Advertisement> titleColumn = grid.addColumn(new ComponentRenderer<>(ad -> {
				Span span = new Span(ad.getTitle());
				span.getElement().setProperty("title", ad.getTitle());
				span.getStyle().set("white-space", "normal").set("overflow-wrap", "anywhere");
				return span;
			}))
			.setAutoWidth(false).setFlexGrow(1)
			.setHeader(createSortableHeader("Title", "title"));

		Column<Advertisement> createdColumn = grid.addColumn(ad -> formatInstant(ad.getCreatedAt()))
			.setAutoWidth(true).setFlexGrow(0)
			.setHeader(createSortableHeader("Created At", "created_at"));

		Column<Advertisement> updatedColumn = grid.addColumn(ad -> formatInstant(ad.getUpdatedAt()))
			.setAutoWidth(true).setFlexGrow(0)
			.setHeader(createSortableHeader("Updated At", "updated_at"));

		Column<Advertisement> userIdColumn = grid.addColumn(Advertisement::getUserId)
			.setTextAlign(ColumnTextAlign.END)
			.setAutoWidth(true).setFlexGrow(0)
			.setHeader(createSortableHeader("User ID", "user_id"));

		Column<Advertisement> actionsColumn = grid.addColumn(new ComponentRenderer<>(ad -> {
				Button edit = new Button(VaadinIcon.EDIT.create());
				edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
				edit.addClickListener(e -> openAdvertisementFormDialog(ad));

				Button delete = new Button(VaadinIcon.TRASH.create());
				delete.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR);
				delete.addClickListener(e -> confirmAndDelete(ad));

				HorizontalLayout layout = new HorizontalLayout(edit, delete);
				layout.setSpacing(false);
				layout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
				return layout;
			}))
			.setAutoWidth(true).setFlexGrow(0)
			.setTextAlign(ColumnTextAlign.CENTER)
			.setHeader("Actions");

		HeaderRow header = grid.appendHeaderRow();
		header.getCell(idColumn).setComponent(filterFields.getIdBlock());
		header.getCell(titleColumn).setComponent(filterFields.getTitleBlock());
		header.getCell(createdColumn).setComponent(filterFields.getCreatedBlock());
		header.getCell(updatedColumn).setComponent(filterFields.getUpdatedBlock());
		header.getCell(actionsColumn).setComponent(createActionBlock(filterFields.getActionBlock()));
	}

	private Component createSortableHeader(String label, String property) {
		Span title = new Span(label);
		SortToggleButton toggle = new SortToggleButton(customSort, property, this::refreshGrid);
		HorizontalLayout layout = new HorizontalLayout(title, toggle);
		layout.setAlignItems(Alignment.CENTER);
		return layout;
	}

	private VerticalLayout createActionBlock(HorizontalLayout previousActions) {
		HorizontalLayout newActions = new HorizontalLayout(createAddButton());
		newActions.setSpacing(false);
		newActions.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

		VerticalLayout actions = new VerticalLayout(previousActions, newActions);
		actions.setSpacing(false);
		return actions;
	}

	private Button createAddButton() {
		Button add = new Button("Add", VaadinIcon.PLUS.create());
		add.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
		add.addClickListener(e -> openAdvertisementFormDialog(null));
		return add;
	}

	private void openAdvertisementFormDialog(Advertisement advertisement) {
		AdvertisementFormDialog dialog = new AdvertisementFormDialog(advertisement, repository);
		dialog.addOpenedChangeListener(event -> {
			if (!event.isOpened()) {
				refreshGrid();
			}
		});
		dialog.open();
	}

	private void confirmAndDelete(Advertisement ad) {
		Dialog dialog = new Dialog();
		dialog.add(new Span("Delete advertisement \"" + ad.getTitle() + "\" (ID " + ad.getId() + ")?"));

		Button confirm = new Button("Delete", e -> {
			try {
				repository.delete(ad);
				Notification.show("Advertisement deleted", 3000, Notification.Position.BOTTOM_START)
					.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
				refreshGrid();
			} catch (Exception ex) {
				Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.BOTTOM_START)
					.addThemeVariants(NotificationVariant.LUMO_ERROR);
			}
			dialog.close();
		});
		confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

		Button cancel = new Button("Cancel", e -> dialog.close());
		cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
		dialog.getFooter().add(cancel, confirm);
		dialog.open();
	}
}
