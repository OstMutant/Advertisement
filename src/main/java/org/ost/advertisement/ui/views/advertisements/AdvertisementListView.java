package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.provider.CallbackDataProvider;
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
import org.ost.advertisement.ui.util.TimeZoneUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@SpringComponent
@UIScope
@PageTitle("Advertisements | Advertisement App")
@Route("advertisements")
@Slf4j
public class AdvertisementListView extends VerticalLayout {

	private final AdvertisementRepository advertisementRepository;
	private Grid<Advertisement> advertisementGrid;

	@Autowired
	public AdvertisementListView(AdvertisementRepository advertisementRepository) {
		this.advertisementRepository = advertisementRepository;
		addClassName("advertisement-list-view");
		setSizeFull();
		configureGrid();
		add(advertisementGrid);
	}

	@PostConstruct
	private void init() {
		CallbackDataProvider<Advertisement, Void> callbackDataProvider = DataProvider.fromCallbacks(
			query -> {
				Sort sort = Sort.by(
					query.getSortOrders().stream()
						.map(sortOrder -> sortOrder.getDirection() == SortDirection.ASCENDING
							? Sort.Order.asc(sortOrder.getSorted())
							: Sort.Order.desc(sortOrder.getSorted()))
						.collect(Collectors.toList()));

				if (sort.isEmpty()) {
					sort = Sort.by("id").ascending();
				}

				PageRequest pageable = PageRequest.of(query.getOffset() / query.getLimit(), query.getLimit(), sort);
				return advertisementRepository.findByFilter(new AdvertisementFilter(), pageable).stream();
			},
			query -> {
				return advertisementRepository.countByFilter(new AdvertisementFilter()).intValue();
			}
		);

		advertisementGrid.setDataProvider(callbackDataProvider);
		advertisementGrid.getDataProvider().refreshAll();
	}

	private void configureGrid() {
		advertisementGrid = new Grid<>(Advertisement.class, false);
		advertisementGrid.setSizeFull();

		ZoneId clientZoneId = ZoneId.of(TimeZoneUtil.getClientTimeZoneId());

		advertisementGrid.addColumn(Advertisement::getId)
			.setHeader("ID")
			.setSortable(true)
			.setSortProperty("id")
			.setAutoWidth(true)
			.setFlexGrow(0)
			.setTextAlign(ColumnTextAlign.END);

		advertisementGrid.addColumn(new ComponentRenderer<>(ad -> {
				Span titleSpan = new Span(ad.getTitle());
				titleSpan.getElement().setProperty("title", ad.getTitle()); // Tooltip
				titleSpan.getStyle()
					.set("white-space", "normal")
					.set("overflow-wrap", "anywhere")
					.set("line-height", "1.4");
				return titleSpan;
			}))
			.setHeader("Title")
			.setSortable(true)
			.setSortProperty("title")
			.setAutoWidth(false)
			.setFlexGrow(1); // Захоплює весь вільний простір

		advertisementGrid.addColumn(Advertisement::getCategory)
			.setHeader("Category")
			.setSortable(true)
			.setSortProperty("category")
			.setAutoWidth(true)
			.setFlexGrow(0);

		advertisementGrid.addColumn(Advertisement::getLocation)
			.setHeader("Location")
			.setSortable(true)
			.setSortProperty("location")
			.setAutoWidth(true)
			.setFlexGrow(0);

		advertisementGrid.addColumn(Advertisement::getStatus)
			.setHeader("Status")
			.setSortable(true)
			.setSortProperty("status")
			.setAutoWidth(true)
			.setFlexGrow(0);

		advertisementGrid.addColumn(ad -> formatInstant(ad.getCreatedAt(), clientZoneId))
			.setHeader("Created At")
			.setSortable(true)
			.setSortProperty("createdAt")
			.setAutoWidth(true)
			.setFlexGrow(0);

		advertisementGrid.addColumn(ad -> formatInstant(ad.getUpdatedAt(), clientZoneId))
			.setHeader("Updated At")
			.setSortable(true)
			.setSortProperty("updatedAt")
			.setAutoWidth(true)
			.setFlexGrow(0);

		advertisementGrid.addColumn(Advertisement::getUserId)
			.setHeader("User ID")
			.setSortable(true)
			.setSortProperty("userId")
			.setAutoWidth(true)
			.setFlexGrow(0)
			.setTextAlign(ColumnTextAlign.END);

		advertisementGrid.addColumn(new ComponentRenderer<>(ad -> {
				Button editButton = new Button(VaadinIcon.EDIT.create());
				editButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);

				Button deleteButton = new Button(VaadinIcon.TRASH.create());
				deleteButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR);

				editButton.addClickListener(e -> openAdvertisementFormDialog(ad));
				deleteButton.addClickListener(e -> confirmAndDelete(ad));

				HorizontalLayout actions = new HorizontalLayout(editButton, deleteButton);
				actions.setSpacing(false);
				actions.setJustifyContentMode(JustifyContentMode.CENTER);
				actions.setWidthFull();
				return actions;
			}))
			.setHeader("Actions")
			.setAutoWidth(true)
			.setFlexGrow(0)
			.setTextAlign(ColumnTextAlign.CENTER);
	}

	private String formatInstant(Instant instant, ZoneId zoneId) {
		if (instant == null) {
			return "N/A";
		}
		return LocalDateTime.ofInstant(instant, zoneId)
			.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
	}

	private void openAdvertisementFormDialog(Advertisement advertisement) {
		AdvertisementFormDialog dialog = new AdvertisementFormDialog(advertisement, advertisementRepository);
		dialog.addOpenedChangeListener(event -> {
			if (!event.isOpened()) {
				advertisementGrid.getDataProvider().refreshAll();
			}
		});
		dialog.open();
	}

	private void confirmAndDelete(Advertisement advertisement) {
		Dialog confirmDialog = new Dialog();
		confirmDialog.add(new Span(
			"Are you sure you want to delete advertisement: " + advertisement.getTitle() + " (ID: "
				+ advertisement.getId() + ")?"));

		Button confirmButton = new Button("Delete", e -> {
			try {
				advertisementRepository.delete(advertisement);
				Notification.show("Advertisement deleted successfully!", 3000, Notification.Position.BOTTOM_START)
					.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
				advertisementGrid.getDataProvider().refreshAll();
				confirmDialog.close();
			} catch (Exception ex) {
				log.error("Failed to delete advertisement: {}", advertisement.getId(), ex);
				Notification.show("Error deleting advertisement: " + ex.getMessage(), 5000,
						Notification.Position.BOTTOM_START)
					.addThemeVariants(NotificationVariant.LUMO_ERROR);
				confirmDialog.close();
			}
		});
		confirmButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

		Button cancelButton = new Button("Cancel", e -> confirmDialog.close());
		cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

		confirmDialog.getFooter().add(cancelButton, confirmButton);
		confirmDialog.open();
	}

	public DataProvider<Advertisement, ?> getDataProvider() {
		return advertisementGrid.getDataProvider();
	}
}
