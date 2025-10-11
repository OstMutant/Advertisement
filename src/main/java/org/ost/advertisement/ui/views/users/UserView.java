package org.ost.advertisement.ui.views.users;

import static org.ost.advertisement.constans.I18nKey.USER_VIEW_CONFIRM_CANCEL_BUTTON;
import static org.ost.advertisement.constans.I18nKey.USER_VIEW_CONFIRM_DELETE_BUTTON;
import static org.ost.advertisement.constans.I18nKey.USER_VIEW_CONFIRM_DELETE_TEXT;
import static org.ost.advertisement.constans.I18nKey.USER_VIEW_HEADER_ACTIONS;
import static org.ost.advertisement.constans.I18nKey.USER_VIEW_HEADER_CREATED;
import static org.ost.advertisement.constans.I18nKey.USER_VIEW_HEADER_EMAIL;
import static org.ost.advertisement.constans.I18nKey.USER_VIEW_HEADER_ID;
import static org.ost.advertisement.constans.I18nKey.USER_VIEW_HEADER_NAME;
import static org.ost.advertisement.constans.I18nKey.USER_VIEW_HEADER_ROLE;
import static org.ost.advertisement.constans.I18nKey.USER_VIEW_HEADER_UPDATED;
import static org.ost.advertisement.constans.I18nKey.USER_VIEW_NOTIFICATION_DELETED;
import static org.ost.advertisement.constans.I18nKey.USER_VIEW_NOTIFICATION_DELETE_ERROR;
import static org.ost.advertisement.constans.I18nKey.USER_VIEW_NOTIFICATION_VALIDATION_FAILED;
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
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.ost.advertisement.dto.filter.UserFilterDto;
import org.ost.advertisement.dto.sort.CustomSort;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.UserService;
import org.ost.advertisement.ui.utils.NotificationType;
import org.ost.advertisement.ui.views.components.PaginationBarModern;
import org.ost.advertisement.ui.views.components.sort.SortToggleButton;

@SpringComponent
@UIScope
public class UserView extends VerticalLayout {

	private final transient UserService userService;
	private final Grid<User> grid = new Grid<>(User.class, false);
	private final PaginationBarModern paginationBar;
	private final transient UserFilterFields filterFields;
	private final transient CustomSort customSort = new CustomSort();
	private final transient I18nService i18n;

	public UserView(UserFilterFields filterFields, UserService userService, I18nService i18n) {
		this.filterFields = filterFields;
		this.userService = userService;
		this.i18n = i18n;
		this.paginationBar = new PaginationBarModern(i18n);
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
		UserFilterDto currentFilter = filterFields.getFilterFieldsProcessor().getNewFilter();
		try {
			List<User> pageData = userService.getFiltered(currentFilter, page, size, customSort.getSort());
			int totalCount = userService.count(currentFilter);
			paginationBar.setTotalCount(totalCount);
			grid.setItems(pageData);
		} catch (ConstraintViolationException ex) {
			showValidationErrors(ex);
			grid.setItems(List.of());
			paginationBar.setTotalCount(0);
		}
	}

	private void configureGrid() {
		grid.setSizeFull();

		Column<User> idColumn = grid.addColumn(User::getId)
			.setAutoWidth(true).setFlexGrow(0).setTextAlign(ColumnTextAlign.END)
			.setHeader(createSortableHeader(i18n.get(USER_VIEW_HEADER_ID), "id"));

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
			.setHeader(createDualSortableHeader(
				i18n.get(USER_VIEW_HEADER_NAME), "name",
				i18n.get(USER_VIEW_HEADER_EMAIL), "email"));

		Column<User> roleColumn = grid.addColumn(user -> user.getRole().name())
			.setAutoWidth(true).setFlexGrow(0)
			.setHeader(createSortableHeader(i18n.get(USER_VIEW_HEADER_ROLE), "role"));

		Column<User> createdColumn = grid.addColumn(user -> formatInstant(user.getCreatedAt()))
			.setAutoWidth(true).setFlexGrow(0)
			.setHeader(createSortableHeader(i18n.get(USER_VIEW_HEADER_CREATED), "createdAt"));

		Column<User> updatedColumn = grid.addColumn(user -> formatInstant(user.getUpdatedAt()))
			.setAutoWidth(true).setFlexGrow(0)
			.setHeader(createSortableHeader(i18n.get(USER_VIEW_HEADER_UPDATED), "updatedAt"));

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
			.setHeader(i18n.get(USER_VIEW_HEADER_ACTIONS))
			.setAutoWidth(true)
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
		UserFormDialog dialog = new UserFormDialog(user, userService, i18n);
		dialog.addOpenedChangeListener(e -> {
			if (!e.isOpened()) {
				refreshGrid();
			}
		});
		dialog.open();
	}

	private void confirmAndDelete(User user) {
		Dialog dialog = new Dialog();
		String confirmText = i18n.get(USER_VIEW_CONFIRM_DELETE_TEXT, user.getName(), user.getId());
		dialog.add(new Span(confirmText));

		Button confirm = new Button(i18n.get(USER_VIEW_CONFIRM_DELETE_BUTTON), e -> {
			try {
				userService.delete(user);
				NotificationType.SUCCESS.show(i18n.get(USER_VIEW_NOTIFICATION_DELETED));
				refreshGrid();
			} catch (Exception ex) {
				NotificationType.ERROR.show(i18n.get(USER_VIEW_NOTIFICATION_DELETE_ERROR, ex.getMessage()));
			}
			dialog.close();
		});
		confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

		Button cancel = new Button(i18n.get(USER_VIEW_CONFIRM_CANCEL_BUTTON), e -> dialog.close());
		cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

		dialog.getFooter().add(cancel, confirm);
		dialog.open();
	}

	private Component createSortableHeader(String label, String property) {
		Span title = new Span(label);
		SortToggleButton toggle = new SortToggleButton(customSort, property, this::refreshGrid, i18n);
		HorizontalLayout layout = new HorizontalLayout(title, toggle);
		layout.setAlignItems(Alignment.CENTER);
		return layout;
	}

	private Component createDualSortableHeader(String label1, String property1, String label2, String property2) {
		HorizontalLayout layout = new HorizontalLayout(
			new Span(label1),
			new SortToggleButton(customSort, property1, this::refreshGrid, i18n),
			new Span(" / "),
			new Span(label2),
			new SortToggleButton(customSort, property2, this::refreshGrid, i18n)
		);
		layout.setAlignItems(Alignment.CENTER);
		return layout;
	}

	private void showValidationErrors(ConstraintViolationException ex) {
		Set<ConstraintViolation<?>> violations = ex.getConstraintViolations();
		String message = violations.stream()
			.map(v -> v.getPropertyPath() + ": " + v.getMessage())
			.distinct()
			.sorted()
			.collect(Collectors.joining("\n"));

		NotificationType.ERROR.show(i18n.get(USER_VIEW_NOTIFICATION_VALIDATION_FAILED) + "\n" + message);
	}
}

