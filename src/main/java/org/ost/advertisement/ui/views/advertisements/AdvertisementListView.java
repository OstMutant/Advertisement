package org.ost.advertisement.ui.views.advertisements;

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
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.ConfigurableFilterDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.dto.AdvertisementFilter;
import org.ost.advertisement.entyties.Advertisement;
import org.ost.advertisement.repository.AdvertisementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@SpringComponent
@UIScope
@PageTitle("Advertisements | Advertisement App")
@Route("advertisements")
@Slf4j
public class AdvertisementListView extends VerticalLayout {

	private final AdvertisementRepository repository;
	private Grid<Advertisement> grid;
	private ConfigurableFilterDataProvider<Advertisement, Void, AdvertisementFilter> dataProvider;
	private final AdvertisementFilterFields filterFields = new AdvertisementFilterFields();

	@Autowired
	public AdvertisementListView(AdvertisementRepository repository) {
		this.repository = repository;
		addClassName("advertisement-list-view");
		setSizeFull();
		configureGrid();
		add(grid);
	}

	@PostConstruct
	private void init() {
		AdvertisementFilter filter = filterFields.getFilter();
		CallbackDataProvider<Advertisement, AdvertisementFilter> callbackDataProvider = DataProvider.fromFilteringCallbacks(
			query -> {
				Sort sort = Sort.by(query.getSortOrders().stream()
					.map(order -> order.getDirection() == SortDirection.ASCENDING
						? Sort.Order.asc(order.getSorted())
						: Sort.Order.desc(order.getSorted()))
					.collect(Collectors.toList()));
				PageRequest pageable = PageRequest.of(query.getOffset() / query.getLimit(), query.getLimit(), sort);
				return repository.findByFilter(query.getFilter().orElse(filter), pageable).stream();
			},
			query -> repository.countByFilter(query.getFilter().orElse(filter)).intValue()
		);
		dataProvider = callbackDataProvider.withConfigurableFilter();
		dataProvider.setFilter(filter);
		grid.setDataProvider(dataProvider);
		filterFields.configure(dataProvider);
	}

	private void configureGrid() {
		grid = new Grid<>(Advertisement.class, false);
		grid.setSizeFull();

		Column<Advertisement> idColumn = grid.addColumn(Advertisement::getId)
			.setHeader("ID").setKey("id").setSortable(true).setSortProperty("id")
			.setAutoWidth(true).setFlexGrow(0).setTextAlign(ColumnTextAlign.END);

		Column<Advertisement> titleColumn = grid.addColumn(new ComponentRenderer<>(ad -> {
				Span span = new Span(ad.getTitle());
				span.getElement().setProperty("title", ad.getTitle());
				span.getStyle().set("white-space", "normal").set("overflow-wrap", "anywhere");
				return span;
			})).setHeader("Title").setKey("title").setSortable(true).setSortProperty("title")
			.setAutoWidth(false).setFlexGrow(1);

		Column<Advertisement> categoryColumn = grid.addColumn(Advertisement::getCategory)
			.setHeader("Category").setKey("category").setSortable(true).setSortProperty("category")
			.setAutoWidth(true).setFlexGrow(0);

		Column<Advertisement> locationColumn = grid.addColumn(Advertisement::getLocation)
			.setHeader("Location").setKey("location").setSortable(true).setSortProperty("location")
			.setAutoWidth(true).setFlexGrow(0);

		Column<Advertisement> statusColumn = grid.addColumn(Advertisement::getStatus)
			.setHeader("Status").setKey("status").setSortable(true).setSortProperty("status")
			.setAutoWidth(true).setFlexGrow(0);

		Column<Advertisement> createdColumn = grid.addColumn(ad -> formatInstant(ad.getCreatedAt()))
			.setHeader("Created At").setKey("createdAt").setSortable(true).setSortProperty("createdAt")
			.setAutoWidth(true).setFlexGrow(0);

		Column<Advertisement> updatedColumn = grid.addColumn(ad -> formatInstant(ad.getUpdatedAt()))
			.setHeader("Updated At").setKey("updatedAt").setSortable(true).setSortProperty("updatedAt")
			.setAutoWidth(true).setFlexGrow(0);

		Column<Advertisement> userIdColumn = grid.addColumn(Advertisement::getUserId)
			.setHeader("User ID").setKey("userId").setSortable(true).setSortProperty("userId")
			.setAutoWidth(true).setFlexGrow(0).setTextAlign(ColumnTextAlign.END);

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
			})).setHeader("Actions").setKey("actions")
			.setAutoWidth(true).setFlexGrow(0).setTextAlign(ColumnTextAlign.CENTER);

		HeaderRow filterRow = grid.appendHeaderRow();
		filterRow.getCell(idColumn).setComponent(filterFields.getIdBlock());
		filterRow.getCell(titleColumn).setComponent(filterFields.getTitleBlock());
		filterRow.getCell(categoryColumn).setComponent(filterFields.getCategoryBlock());
		filterRow.getCell(locationColumn).setComponent(filterFields.getLocationBlock());
		filterRow.getCell(statusColumn).setComponent(filterFields.getStatusBlock());
		filterRow.getCell(createdColumn).setComponent(filterFields.getCreatedBlock());
		filterRow.getCell(updatedColumn).setComponent(filterFields.getUpdatedBlock());
		filterRow.getCell(actionsColumn).setComponent(filterFields.getActionBlock());
	}

	private String formatInstant(Instant instant) {
		if (instant == null) {
			return "N/A";
		}
		return LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
			.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
	}

	private void openAdvertisementFormDialog(Advertisement ad) {
		AdvertisementFormDialog dialog = new AdvertisementFormDialog(ad, repository);
		dialog.addOpenedChangeListener(e -> {
			if (!e.isOpened()) {
				refreshAll();
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
				refreshAll();
			} catch (Exception ex) {
				log.error("Error deleting advertisement", ex);
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

	public void refreshAll() {
		if (dataProvider != null) {
			dataProvider.refreshAll();
		}
	}
}
