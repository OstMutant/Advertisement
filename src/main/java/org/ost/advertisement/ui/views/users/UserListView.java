package org.ost.advertisement.ui.views.users;

import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.dto.UserFilter;
import org.ost.advertisement.entyties.User;
import org.ost.advertisement.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.component.orderedlayout.FlexComponent;


import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

@SpringComponent
@UIScope
@PageTitle("Users | Advertisement App")
@Route("users")
@Slf4j
public class UserListView extends VerticalLayout {

	private final UserRepository userRepository;
	private Grid<User> userGrid;

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
		CallbackDataProvider<User, Void> callbackDataProvider = DataProvider.fromCallbacks(
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
				return userRepository.findByFilter(new UserFilter(), pageable).stream();
			},
			query -> {
				return userRepository.countByFilter(new UserFilter()).intValue();
			}
		);

		userGrid.setDataProvider(callbackDataProvider);
		userGrid.getDataProvider().refreshAll();
	}

	private void configureGrid() {
		userGrid = new Grid<>(User.class, false);
		userGrid.setSizeFull();

		userGrid.addColumn(User::getId)
			.setHeader("ID")
			.setSortable(true)
			.setSortProperty("id")
			.setAutoWidth(true) // Auto-size to fit content
			.setFlexGrow(0) // Don't grow
			.setTextAlign(ColumnTextAlign.END);

		userGrid.addColumn(new ComponentRenderer<>(user -> {
				Span nameSpan = new Span(user.getName());
				nameSpan.getElement().setProperty("title", user.getName()); // Tooltip
				nameSpan.getStyle()
					.set("white-space", "normal")
					.set("overflow-wrap", "anywhere")
					.set("line-height", "1.4");
				return nameSpan;
			}))
			.setHeader("Name")
			.setSortable(true)
			.setSortProperty("name")
			.setAutoWidth(false) // Disable auto-width to allow flex-grow
			.setFlexGrow(1); // Take up all remaining space

		userGrid.addColumn(user -> formatInstant(user.getCreatedAt()))
			.setHeader("Created At")
			.setSortable(true)
			.setSortProperty("createdAt")
			.setAutoWidth(true)
			.setFlexGrow(0);

		userGrid.addColumn(user -> formatInstant(user.getUpdatedAt()))
			.setHeader("Updated At")
			.setSortable(true)
			.setSortProperty("updatedAt")
			.setAutoWidth(true)
			.setFlexGrow(0);

		userGrid.addColumn(new ComponentRenderer<>(user -> {
				Button editButton = new Button(VaadinIcon.EDIT.create());
				editButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);

				Button deleteButton = new Button(VaadinIcon.TRASH.create());
				deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY_INLINE);

				editButton.addClickListener(e -> openUserFormDialog(user));
				deleteButton.addClickListener(e -> confirmAndDelete(user));

				HorizontalLayout actionsLayout = new HorizontalLayout(editButton, deleteButton);
				actionsLayout.setSpacing(false); // Reduce spacing between buttons
				actionsLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END); // Align buttons to the right
				return actionsLayout;
			})).setHeader("Actions")
			.setAutoWidth(true) // Auto-size to fit content
			.setFlexGrow(0) // Don't grow
			.setTextAlign(ColumnTextAlign.END); // Align content of the column to the right
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
		dialog.addOpenedChangeListener(event -> {
			if (!event.isOpened()) {
				userGrid.getDataProvider().refreshAll();
			}
		});
		dialog.open();
	}

	private void confirmAndDelete(User user) {
		Dialog confirmDialog = new Dialog();
		confirmDialog.add(new Span("Are you sure you want to delete user: " + user.getName() + " (ID: " + user.getId() + ")?"));

		Button confirmButton = new Button("Delete", e -> {
			try {
				userRepository.delete(user);
				Notification.show("User deleted successfully!", 3000, Notification.Position.BOTTOM_START)
					.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
				userGrid.getDataProvider().refreshAll();
				confirmDialog.close();
			} catch (Exception ex) {
				log.error("Failed to delete user: {}", user.getId(), ex);
				Notification.show("Error deleting user: " + ex.getMessage(), 5000, Notification.Position.BOTTOM_START)
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

	public DataProvider<User, ?> getDataProvider() {
		return userGrid.getDataProvider();
	}
}
