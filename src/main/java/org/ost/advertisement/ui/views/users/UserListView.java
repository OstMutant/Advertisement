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
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.ost.advertisement.dto.UserFilter;
import org.ost.advertisement.entyties.User;
import org.ost.advertisement.repository.UserRepository;
import org.ost.advertisement.ui.components.PaginationBarModern;
import org.ost.advertisement.ui.components.SortToggleButton;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;

@PageTitle("Users | Advertisement App")
@Route("users")
public class UserListView extends VerticalLayout {

	private final UserRepository repository;
	private final Grid<User> grid = new Grid<>(User.class, false);
	private final PaginationBarModern paginationBar = new PaginationBarModern();
	private final UserFilterFields filterFields = new UserFilterFields();
	private Sort currentSort = Sort.unsorted();

	public UserListView(UserRepository repository) {
		this.repository = repository;
		addClassName("user-list-view");
		setSizeFull();

		paginationBar.setPageSize(25);
		paginationBar.setPageChangeListener(event -> refreshGrid());

		filterFields.configure(() -> {
			paginationBar.setTotalCount(0);
			refreshGrid();
		});

		configureGrid();
		add(grid, paginationBar);
		refreshGrid();
	}

	public void refreshGrid() {
		int page = paginationBar.getCurrentPage();
		int size = paginationBar.getPageSize();
		UserFilter currentFilter = filterFields.getNewFilter();
		List<User> pageData = repository.findByFilter(currentFilter, PageRequest.of(page, size, currentSort));
		int totalCount = repository.countByFilter(currentFilter).intValue();

		paginationBar.setTotalCount(totalCount);
		grid.setItems(pageData);
	}

	private void configureGrid() {
		grid.setSizeFull();

		Column<User> idColumn = grid.addColumn(User::getId)
			.setHeader("ID").setKey("id")
			.setAutoWidth(true).setFlexGrow(0).setTextAlign(ColumnTextAlign.END);

		Column<User> nameColumn = grid.addColumn(new ComponentRenderer<>(user -> {
				Span nameSpan = new Span(user.getName());
				nameSpan.getStyle()
					.set("font-weight", "500")
					.set("white-space", "normal")
					.set("overflow-wrap", "anywhere");

				Span emailSpan = new Span(user.getEmail());
				emailSpan.getStyle()
					.set("font-size", "12px")
					.set("color", "gray")
					.set("white-space", "normal")
					.set("overflow-wrap", "anywhere");

				VerticalLayout layout = new VerticalLayout(nameSpan, emailSpan);
				layout.setSpacing(false);
				layout.setPadding(false);
				layout.setMargin(false);
				return layout;
			}))
			.setHeader("Name / Email").setKey("name")
			.setAutoWidth(false).setFlexGrow(1);

		Column<User> roleColumn = grid.addColumn(user -> user.getRole().name())
			.setKey("role")
			.setAutoWidth(true).setFlexGrow(0);

		SortToggleButton roleSort = new SortToggleButton(currentSort, "role", order -> {
			List<Order> orders = currentSort.stream().filter(v -> !"role".equals(v.getProperty()))
				.collect(Collectors.toList());
			if (Objects.nonNull(order)) {
				orders.add(order);
			}
			currentSort = Sort.by(orders);
			refreshGrid();
		});

		HorizontalLayout roleHeader = new HorizontalLayout(new Span("Role"), roleSort);
		roleHeader.setAlignItems(FlexComponent.Alignment.CENTER);
		roleColumn.setHeader(roleHeader);

		Column<User> createdColumn = grid.addColumn(user -> formatInstant(user.getCreatedAt()))
			.setHeader("Created At").setKey("createdAt")
			.setAutoWidth(true).setFlexGrow(0);

		Column<User> updatedColumn = grid.addColumn(user -> formatInstant(user.getUpdatedAt()))
			.setHeader("Updated At").setKey("updatedAt")
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
			.setHeader("Actions").setAutoWidth(true)
			.setFlexGrow(0).setTextAlign(ColumnTextAlign.CENTER);

		HeaderRow filterRow = grid.appendHeaderRow();
		filterRow.getCell(idColumn).setComponent(filterFields.getIdBlock());
		filterRow.getCell(nameColumn).setComponent(filterFields.getNameBlock());
		filterRow.getCell(roleColumn).setComponent(filterFields.getRoleBlock());
		filterRow.getCell(createdColumn).setComponent(filterFields.getCreatedAtBlock());
		filterRow.getCell(updatedColumn).setComponent(filterFields.getUpdatedAtBlock());
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
