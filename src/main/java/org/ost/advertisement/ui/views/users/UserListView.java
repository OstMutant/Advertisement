package org.ost.advertisement.ui.views.users;

import static org.ost.advertisement.ui.utils.TimeZoneUtil.formatInstant;

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
import java.util.List;
import org.ost.advertisement.dto.UserFilter;
import org.ost.advertisement.entyties.User;
import org.ost.advertisement.repository.UserRepository;
import org.ost.advertisement.ui.components.PaginationBarModern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@SpringComponent
@UIScope
@PageTitle("Users | Advertisement App")
@Route("users")
public class UserListView extends VerticalLayout {

	private final UserRepository repository;
	private final Grid<User> grid = new Grid<>(User.class, false);
	private final PaginationBarModern paginationBar = new PaginationBarModern();
	private final UserFilterFields filterFields = new UserFilterFields();
	private UserFilter currentFilter = new UserFilter();
	private Sort currentSort = Sort.unsorted();

	@Autowired
	public UserListView(UserRepository repository) {
		this.repository = repository;
		addClassName("user-list-view");
		setSizeFull();

		paginationBar.setPageSize(25);
		paginationBar.setPageChangeListener(event -> refreshGrid());

		grid.addSortListener(event -> {
			List<Sort.Order> orders = event.getSortOrder().stream()
				.map(order -> {
					String property = order.getSorted().getKey();
					return order.getDirection() == SortDirection.ASCENDING
						? Sort.Order.asc(property)
						: Sort.Order.desc(property);
				}).toList();

			currentSort = Sort.by(orders);
			refreshGrid();
		});

		paginationBar.setPageChangeListener(e -> refreshGrid());
		filterFields.configure(() -> {
			currentFilter = filterFields.getFilter();
			paginationBar.setTotalCount(0);
			refreshGrid();
		});

		configureGrid();
		add(grid, paginationBar);
	}

	@PostConstruct
	private void init() {
		refreshGrid();
	}

	public void refreshGrid() {
		int page = paginationBar.getCurrentPage();
		int size = paginationBar.getPageSize();
		PageRequest pageable = PageRequest.of(page, size, currentSort);

		List<User> pageData = repository.findByFilter(currentFilter, pageable);
		int totalCount = repository.countByFilter(currentFilter).intValue();

		paginationBar.setTotalCount(totalCount);
		grid.setItems(pageData);
	}

	private void configureGrid() {
		grid.setSizeFull();

		Column<User> idColumn = grid.addColumn(User::getId)
			.setHeader("ID").setKey("id").setSortable(true).setSortProperty("id")
			.setAutoWidth(true).setFlexGrow(0).setTextAlign(ColumnTextAlign.END);

		Column<User> nameColumn = grid.addColumn(new ComponentRenderer<>(user -> {
				Span span = new Span(user.getName());
				span.getElement().setProperty("title", user.getName());
				span.getStyle().set("white-space", "normal").set("overflow-wrap", "anywhere");
				return span;
			}))
			.setHeader("Name").setKey("name").setSortable(true).setSortProperty("name")
			.setAutoWidth(false).setFlexGrow(1);

		Column<User> createdColumn = grid.addColumn(user -> formatInstant(user.getCreatedAt()))
			.setHeader("Created At").setKey("createdAt").setSortable(true).setSortProperty("createdAt")
			.setAutoWidth(true).setFlexGrow(0);

		Column<User> updatedColumn = grid.addColumn(user -> formatInstant(user.getUpdatedAt()))
			.setHeader("Updated At").setKey("updatedAt").setSortable(true).setSortProperty("updatedAt")
			.setAutoWidth(true).setFlexGrow(0);

		Column<User> actionsColumn = grid.addColumn(new ComponentRenderer<>(user -> {
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

		HeaderRow filterRow = grid.appendHeaderRow();
		filterRow.getCell(idColumn).setComponent(filterFields.getIdBlock());
		filterRow.getCell(nameColumn).setComponent(filterFields.getNameBlock());
		filterRow.getCell(createdColumn).setComponent(filterFields.getCreatedBlock());
		filterRow.getCell(updatedColumn).setComponent(filterFields.getUpdatedBlock());
		filterRow.getCell(actionsColumn).setComponent(filterFields.getActionBlock());
	}

	private void openUserFormDialog(User user) {
		UserFormDialog dialog = new UserFormDialog(user, repository);
		dialog.addOpenedChangeListener(e -> {
			if (!e.isOpened()) {
				refreshGrid();
			}
		});
		dialog.open();
	}

	private void confirmAndDelete(User user) {
		Dialog dialog = new Dialog();
		dialog.add(new Span("Delete user " + user.getName() + " (ID " + user.getId() + ")?"));

		Button confirm = new Button("Delete", e -> {
			try {
				repository.delete(user);
				Notification notification = Notification.show("User deleted", 3000, Notification.Position.BOTTOM_START);
				notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
				refreshGrid();
			} catch (Exception ex) {
				Notification notification = Notification.show("Error: " + ex.getMessage(), 5000,
					Notification.Position.BOTTOM_START);
				notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
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
