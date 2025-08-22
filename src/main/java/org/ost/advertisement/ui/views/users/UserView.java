package org.ost.advertisement.ui.views.users;

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
import com.vaadin.flow.spring.annotation.SpringComponent;
import java.util.List;
import org.ost.advertisement.dto.filter.UserFilter;
import org.ost.advertisement.dto.sort.CustomSort;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.services.UserService;
import org.ost.advertisement.ui.utils.SessionUtil;
import org.ost.advertisement.ui.views.components.PaginationBarModern;
import org.ost.advertisement.ui.views.components.sort.SortToggleButton;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.Sort;

@SpringComponent
@Scope("prototype")
public class UserView extends VerticalLayout {

	private final UserService userService;
	private final Grid<User> grid = new Grid<>(User.class, false);
	private final PaginationBarModern paginationBar = new PaginationBarModern();
	private final UserFilterFields filterFields;
	private final CustomSort customSort = new CustomSort(Sort.unsorted());

	public UserView(UserFilterFields filterFields, UserService userService) {
		this.filterFields = filterFields;
		this.userService = userService;
		addClassName("user-list-view");
		setSizeFull();
		setPadding(false);
		setSpacing(false);

		paginationBar.setPageChangeListener(event -> refreshGrid());

		filterFields.eventProcessor(() -> {
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
		UserFilter currentFilter = filterFields.getNewFilter();
		List<User> pageData = userService.getFiltered(currentFilter, page, size, customSort.getSort());
		int totalCount = userService.count(currentFilter);

		paginationBar.setTotalCount(totalCount);
		grid.setItems(pageData);
	}

	private void configureGrid() {
		grid.setSizeFull();

		Column<User> idColumn = grid.addColumn(User::getId)
			.setAutoWidth(true).setFlexGrow(0).setTextAlign(ColumnTextAlign.END)
			.setHeader(createSortableHeader("ID", "id"));

		Column<User> nameAndEmailColumn = grid.addColumn(new ComponentRenderer<>(user -> {
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
			.setAutoWidth(false).setFlexGrow(1)
			.setHeader(createDualSortableHeader("Name", "name", "Email", "email"));

		Column<User> roleColumn = grid.addColumn(user -> user.getRole().name())
			.setAutoWidth(true).setFlexGrow(0)
			.setHeader(createSortableHeader("Role", "role"));

		Column<User> createdColumn = grid.addColumn(user -> formatInstant(user.getCreatedAt()))
			.setAutoWidth(true).setFlexGrow(0)
			.setHeader(createSortableHeader("Created At", "createdAt"));

		Column<User> updatedColumn = grid.addColumn(user -> formatInstant(user.getUpdatedAt()))
			.setAutoWidth(true).setFlexGrow(0)
			.setHeader(createSortableHeader("Updated At", "updatedAt"));

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
		VerticalLayout nameAndEmailLayout = new VerticalLayout(filterFields.getNameBlock(),
			filterFields.getEmailBlock());
		nameAndEmailLayout.setSpacing(false);
		nameAndEmailLayout.setPadding(false);
		nameAndEmailLayout.setMargin(false);
		filterRow.getCell(nameAndEmailColumn).setComponent(nameAndEmailLayout);
		filterRow.getCell(roleColumn).setComponent(filterFields.getRoleBlock());
		filterRow.getCell(createdColumn).setComponent(filterFields.getCreatedAtBlock());
		filterRow.getCell(updatedColumn).setComponent(filterFields.getUpdatedAtBlock());
		filterRow.getCell(actionsColumn).setComponent(filterFields.getActionBlock());
	}

	private void openUserFormDialog(User user) {
		UserFormDialog dialog = new UserFormDialog(user, userService);
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
				userService.delete(SessionUtil.getCurrentUser(), user);
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

	private Component createSortableHeader(String label, String property) {
		Span title = new Span(label);
		SortToggleButton toggle = new SortToggleButton(customSort, property, this::refreshGrid);
		HorizontalLayout layout = new HorizontalLayout(title, toggle);
		layout.setAlignItems(Alignment.CENTER);
		return layout;
	}

	private Component createDualSortableHeader(String label1, String property1, String label2, String property2) {
		HorizontalLayout layout = new HorizontalLayout(
			new Span(label1),
			new SortToggleButton(customSort, property1, this::refreshGrid),
			new Span(" / "),
			new Span(label2),
			new SortToggleButton(customSort, property2, this::refreshGrid)
		);
		layout.setAlignItems(Alignment.CENTER);
		return layout;
	}
}
