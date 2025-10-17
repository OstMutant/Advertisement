package org.ost.advertisement.ui.views.users;

import static org.ost.advertisement.constans.I18nKey.USER_VIEW_HEADER_ACTIONS;
import static org.ost.advertisement.constans.I18nKey.USER_VIEW_HEADER_CREATED;
import static org.ost.advertisement.constans.I18nKey.USER_VIEW_HEADER_EMAIL;
import static org.ost.advertisement.constans.I18nKey.USER_VIEW_HEADER_ID;
import static org.ost.advertisement.constans.I18nKey.USER_VIEW_HEADER_NAME;
import static org.ost.advertisement.constans.I18nKey.USER_VIEW_HEADER_ROLE;
import static org.ost.advertisement.constans.I18nKey.USER_VIEW_HEADER_UPDATED;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.HeaderRow;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import java.util.function.Consumer;
import org.ost.advertisement.dto.sort.CustomSort;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.TimeZoneUtil;
import org.ost.advertisement.ui.views.components.sort.SortToggleButton;

public class UserGridConfigurator {

	public static void configure(Grid<User> grid,
								 UserFilterFields filterFields,
								 I18nService i18n,
								 CustomSort customSort,
								 Consumer<User> onEdit,
								 Consumer<User> onDelete,
								 Runnable refreshGrid) {

		grid.setSizeFull();

		var idColumn = grid.addColumn(User::getId)
			.setAutoWidth(true).setFlexGrow(0).setTextAlign(ColumnTextAlign.END)
			.setHeader(sortableHeader(i18n.get(USER_VIEW_HEADER_ID), "id", customSort, i18n, refreshGrid));

		var nameAndEmailColumn = grid.addColumn(new ComponentRenderer<>(user -> {
				Span nameSpan = new Span(user.name());
				nameSpan.getStyle().set("font-weight", "500").set("white-space", "normal").set("overflow-wrap", "anywhere");

				Span emailSpan = new Span(user.email());
				emailSpan.getStyle().set("font-size", "12px").set("color", "gray").set("white-space", "normal")
					.set("overflow-wrap", "anywhere");

				VerticalLayout layout = new VerticalLayout(nameSpan, emailSpan);
				layout.setSpacing(false);
				layout.setPadding(false);
				layout.setMargin(false);
				return layout;
			}))
			.setAutoWidth(false).setFlexGrow(1)
			.setHeader(dualSortableHeader(
				i18n.get(USER_VIEW_HEADER_NAME), "name",
				i18n.get(USER_VIEW_HEADER_EMAIL), "email",
				customSort, i18n, refreshGrid));

		var roleColumn = grid.addColumn(user -> user.role().name())
			.setAutoWidth(true).setFlexGrow(0)
			.setHeader(sortableHeader(i18n.get(USER_VIEW_HEADER_ROLE), "role", customSort, i18n, refreshGrid));

		var createdColumn = grid.addColumn(user -> TimeZoneUtil.formatInstant(user.createdAt()))
			.setAutoWidth(true).setFlexGrow(0)
			.setHeader(sortableHeader(i18n.get(USER_VIEW_HEADER_CREATED), "createdAt", customSort, i18n, refreshGrid));

		var updatedColumn = grid.addColumn(user -> TimeZoneUtil.formatInstant(user.updatedAt()))
			.setAutoWidth(true).setFlexGrow(0)
			.setHeader(sortableHeader(i18n.get(USER_VIEW_HEADER_UPDATED), "updatedAt", customSort, i18n, refreshGrid));

		var actionsColumn = grid.addColumn(new ComponentRenderer<>(user -> {
				Button edit = new Button(VaadinIcon.EDIT.create());
				edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
				edit.addClickListener(e -> onEdit.accept(user));

				Button delete = new Button(VaadinIcon.TRASH.create());
				delete.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR);
				delete.addClickListener(e -> onDelete.accept(user));

				HorizontalLayout layout = new HorizontalLayout(edit, delete);
				layout.setSpacing(false);
				layout.setJustifyContentMode(HorizontalLayout.JustifyContentMode.CENTER);
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

	private static Component sortableHeader(String label, String property, CustomSort sort, I18nService i18n,
											Runnable refreshGrid) {
		Span title = new Span(label);
		SortToggleButton toggle = new SortToggleButton(sort, property, refreshGrid, i18n);
		HorizontalLayout layout = new HorizontalLayout(title, toggle);
		layout.setAlignItems(VerticalLayout.Alignment.CENTER);
		return layout;
	}

	private static Component dualSortableHeader(String label1, String property1, String label2, String property2,
												CustomSort sort, I18nService i18n, Runnable refreshGrid) {
		HorizontalLayout layout = new HorizontalLayout(
			new Span(label1),
			new SortToggleButton(sort, property1, refreshGrid, i18n),
			new Span(" / "),
			new Span(label2),
			new SortToggleButton(sort, property2, refreshGrid, i18n)
		);
		layout.setAlignItems(VerticalLayout.Alignment.CENTER);
		return layout;
	}
}
