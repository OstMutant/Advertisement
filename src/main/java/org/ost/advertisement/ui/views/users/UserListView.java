package org.ost.advertisement.ui.views.users;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
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
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.ConfigurableFilterDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
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
import org.ost.advertisement.dto.UserFilter;
import org.ost.advertisement.entyties.User;
import org.ost.advertisement.repository.UserRepository;
import org.ost.advertisement.ui.utils.FilterHighlighterUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@SpringComponent
@UIScope
@PageTitle("Users | Advertisement App")
@Route("users")
@Slf4j
public class UserListView extends VerticalLayout {

	private final UserRepository userRepository;
	private final UserFilter userFilter = new UserFilter();
	private Grid<User> userGrid;
	private ConfigurableFilterDataProvider<User, Void, UserFilter> dataProvider;

	private NumberField idFilter;
	private TextField nameFilter;
	private DatePicker createdStart;
	private DatePicker updatedStart;
	private Button applyFilterButton;
	private Button clearFilterButton;

	private final UserFilter defaultFilter = new UserFilter();


	@Autowired
	public UserListView(UserRepository userRepository) {
		this.userRepository = userRepository;
		addClassName("user-list-view");
		setSizeFull();
		configureGrid();
		add(userGrid);
	}

	@PostConstruct
	private void init() {
		CallbackDataProvider<User, UserFilter> callbackDataProvider = DataProvider.fromFilteringCallbacks(
			query -> {
				Sort sort = Sort.by(query.getSortOrders().stream()
					.map(order -> order.getDirection() == SortDirection.ASCENDING
						? Sort.Order.asc(order.getSorted())
						: Sort.Order.desc(order.getSorted()))
					.collect(Collectors.toList()));
				PageRequest pageable = PageRequest.of(query.getOffset() / query.getLimit(), query.getLimit(), sort);
				return userRepository.findByFilter(query.getFilter().orElse(userFilter), pageable).stream();
			},
			query -> userRepository.countByFilter(query.getFilter().orElse(userFilter)).intValue()
		);
		dataProvider = callbackDataProvider.withConfigurableFilter();
		userGrid.setDataProvider(dataProvider);
		dataProvider.setFilter(userFilter);
		configureFilterStyling();
	}

	private void configureGrid() {
		userGrid = new Grid<>(User.class, false);
		userGrid.setSizeFull();

		Column<User> idColumn = userGrid.addColumn(User::getId)
			.setHeader("ID").setKey("id").setSortable(true).setSortProperty("id")
			.setAutoWidth(true).setFlexGrow(0).setTextAlign(ColumnTextAlign.END);

		Column<User> nameColumn = userGrid.addColumn(new ComponentRenderer<>(user -> {
				Span span = new Span(user.getName());
				span.getElement().setProperty("title", user.getName());
				span.getStyle().set("white-space", "normal").set("overflow-wrap", "anywhere");
				return span;
			}))
			.setHeader("Name").setKey("name").setSortable(true).setSortProperty("name")
			.setAutoWidth(false).setFlexGrow(1);

		Column<User> createdColumn = userGrid.addColumn(user -> formatInstant(user.getCreatedAt()))
			.setHeader("Created At").setKey("createdAt").setSortable(true).setSortProperty("createdAt")
			.setAutoWidth(true).setFlexGrow(0);

		Column<User> updatedColumn = userGrid.addColumn(user -> formatInstant(user.getUpdatedAt()))
			.setHeader("Updated At").setKey("updatedAt").setSortable(true).setSortProperty("updatedAt")
			.setAutoWidth(true).setFlexGrow(0);

		Column<User> actionsColumn = userGrid.addColumn(new ComponentRenderer<>(user -> {
				Button edit = new Button(VaadinIcon.EDIT.create());
				edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
				edit.addClickListener(e -> openUserFormDialog(user));

				Button delete = new Button(VaadinIcon.TRASH.create());
				delete.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR);
				delete.addClickListener(e -> confirmAndDelete(user));

				HorizontalLayout layout = new HorizontalLayout(edit, delete);
				layout.setSpacing(false);
				layout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
				return layout;
			}))
			.setHeader("Actions").setAutoWidth(true).setFlexGrow(0).setTextAlign(ColumnTextAlign.CENTER);

		HeaderRow filterRow = userGrid.appendHeaderRow();

		idFilter = new NumberField();
		idFilter.setWidth("100px");
		idFilter.setClearButtonVisible(true);
		idFilter.setPlaceholder("Min ID");
		idFilter.addValueChangeListener(e -> {
			userFilter.setStartId(e.getValue() != null ? e.getValue().longValue() : null);
			updateFilterButtonState();
		});
		filterRow.getCell(idColumn).setComponent(idFilter);

		nameFilter = new TextField();
		nameFilter.setWidthFull();
		nameFilter.setPlaceholder("Name...");
		nameFilter.setClearButtonVisible(true);
		nameFilter.addValueChangeListener(e -> {
			userFilter.setNameFilter(e.getValue());
			updateFilterButtonState();
		});
		filterRow.getCell(nameColumn).setComponent(nameFilter);

		createdStart = new DatePicker();
		createdStart.setWidth("140px");
		createdStart.setPlaceholder("From");
		createdStart.addValueChangeListener(e -> {
			userFilter.setCreatedAtStart(e.getValue() != null ?
				e.getValue().atStartOfDay(ZoneId.systemDefault()).toInstant() : null);
			updateFilterButtonState();
		});
		filterRow.getCell(createdColumn).setComponent(createdStart);

		updatedStart = new DatePicker();
		updatedStart.setWidth("140px");
		updatedStart.setPlaceholder("From");
		updatedStart.addValueChangeListener(e -> {
			userFilter.setUpdatedAtStart(e.getValue() != null ?
				e.getValue().atStartOfDay(ZoneId.systemDefault()).toInstant() : null);
			updateFilterButtonState();
		});
		filterRow.getCell(updatedColumn).setComponent(updatedStart);

		applyFilterButton = new Button(VaadinIcon.FILTER.create());
		applyFilterButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_PRIMARY);
		applyFilterButton.getElement().setProperty("title", "Apply filters");
		applyFilterButton.addClickListener(e -> dataProvider.setFilter(userFilter));

		clearFilterButton = new Button(VaadinIcon.ERASER.create());
		clearFilterButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY);
		clearFilterButton.getElement().setProperty("title", "Clear filters");
		clearFilterButton.addClickListener(e -> {
			idFilter.clear();
			nameFilter.clear();
			createdStart.clear();
			updatedStart.clear();
			userFilter.clear();
			dataProvider.setFilter(userFilter);
			updateFilterButtonState();
		});

		HorizontalLayout actionsHeader = new HorizontalLayout(applyFilterButton, clearFilterButton);
		actionsHeader.setSpacing(false);
		actionsHeader.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
		filterRow.getCell(actionsColumn).setComponent(actionsHeader);
	}

	private void updateFilterButtonState() {
		if (isFilterActive()) {
			applyFilterButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
		} else {
			applyFilterButton.removeThemeVariants(ButtonVariant.LUMO_CONTRAST);
		}
	}

	private boolean isFilterActive() {
		return userFilter.getNameFilter() != null && !userFilter.getNameFilter().isBlank()
			|| userFilter.getStartId() != null
			|| userFilter.getCreatedAtStart() != null
			|| userFilter.getUpdatedAtStart() != null;
	}

	public void refreshAll() {
		if (dataProvider != null) {
			dataProvider.refreshAll();
		}
	}

	private String formatInstant(Instant instant) {
		if (instant == null) {
			return "N/A";
		}
		return LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
			.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
	}

	private void openUserFormDialog(User user) {
		UserFormDialog dialog = new UserFormDialog(user, userRepository);
		dialog.addOpenedChangeListener(e -> {
			if (!e.isOpened()) {
				refreshAll();
			}
		});
		dialog.open();
	}

	private void confirmAndDelete(User user) {
		Dialog dialog = new Dialog();
		dialog.add(new Span("Delete user " + user.getName() + " (ID " + user.getId() + ")?"));

		Button confirm = new Button("Delete", e -> {
			try {
				userRepository.delete(user);
				Notification.show("User deleted", 3000, Notification.Position.BOTTOM_START)
					.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
				dataProvider.refreshAll();
			} catch (Exception ex) {
				log.error("Error deleting user", ex);
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

	private void configureFilterStyling() {
		nameFilter.setValueChangeMode(ValueChangeMode.EAGER);
		nameFilter.addValueChangeListener(e -> {
			userFilter.setNameFilter(e.getValue());
			highlightChangedFilters(true);
		});

		idFilter.setValueChangeMode(ValueChangeMode.EAGER);
		idFilter.addValueChangeListener(e -> {
			userFilter.setStartId(e.getValue() != null ? e.getValue().longValue() : null);
			highlightChangedFilters(true);
		});

		createdStart.addValueChangeListener(e -> {
			userFilter.setCreatedAtStart(e.getValue() != null ?
				e.getValue().atStartOfDay(ZoneId.systemDefault()).toInstant() : null);
			highlightChangedFilters(true);
		});

		updatedStart.addValueChangeListener(e -> {
			userFilter.setUpdatedAtStart(e.getValue() != null ?
				e.getValue().atStartOfDay(ZoneId.systemDefault()).toInstant() : null);
			highlightChangedFilters(true);
		});

		applyFilterButton.addClickListener(e -> {
			dataProvider.setFilter(userFilter);
			defaultFilter.copyFrom(userFilter);
			highlightChangedFilters(true);
		});

		clearFilterButton.addClickListener(e -> {
			idFilter.clear();
			nameFilter.clear();
			createdStart.clear();
			updatedStart.clear();
			userFilter.clear();
			defaultFilter.clear();
			dataProvider.setFilter(userFilter);
			highlightChangedFilters(false);
		});
	}

	private void highlightChangedFilters(boolean enable) {
		if (!enable) {
			FilterHighlighterUtil.clearHighlight(nameFilter, idFilter, createdStart, updatedStart);
			return;
		}
		FilterHighlighterUtil.highlight(nameFilter, userFilter.getNameFilter(), defaultFilter.getNameFilter());
		FilterHighlighterUtil.highlight(idFilter, userFilter.getStartId(), defaultFilter.getStartId());
		FilterHighlighterUtil.highlight(createdStart, userFilter.getCreatedAtStart(),
			defaultFilter.getCreatedAtStart());
		FilterHighlighterUtil.highlight(updatedStart, userFilter.getUpdatedAtStart(),
			defaultFilter.getUpdatedAtStart());
	}
}
