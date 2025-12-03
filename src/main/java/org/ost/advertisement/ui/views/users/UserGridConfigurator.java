package org.ost.advertisement.ui.views.users;

import static org.ost.advertisement.constants.I18nKey.USER_VIEW_HEADER_ACTIONS;
import static org.ost.advertisement.constants.I18nKey.USER_VIEW_HEADER_CREATED;
import static org.ost.advertisement.constants.I18nKey.USER_VIEW_HEADER_EMAIL;
import static org.ost.advertisement.constants.I18nKey.USER_VIEW_HEADER_ID;
import static org.ost.advertisement.constants.I18nKey.USER_VIEW_HEADER_NAME;
import static org.ost.advertisement.constants.I18nKey.USER_VIEW_HEADER_ROLE;
import static org.ost.advertisement.constants.I18nKey.USER_VIEW_HEADER_UPDATED;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.HeaderRow;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.services.I18nService;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserGridConfigurator {

	public static void configure(Grid<User> grid,
								 UserQueryBlock queryBlock,
								 I18nService i18n,
								 Consumer<User> onEdit,
								 Consumer<User> onDelete) {

		grid.setSizeFull();

		var idColumn = grid.addColumn(User::getId)
			.setAutoWidth(true).setFlexGrow(0).setTextAlign(ColumnTextAlign.END)
			.setHeader(sortableHeader(i18n.get(USER_VIEW_HEADER_ID), queryBlock.getIdSortIcon()));

		var nameAndEmailColumn = grid.addColumn(new ComponentRenderer<>(user -> {
				Span nameSpan = new Span(user.getName());
				nameSpan.getStyle().set("font-weight", "500").set("white-space", "normal").set("overflow-wrap", "anywhere");

				Span emailSpan = new Span(user.getEmail());
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
				i18n.get(USER_VIEW_HEADER_NAME), queryBlock.getNameSortIcon(),
				i18n.get(USER_VIEW_HEADER_EMAIL), queryBlock.getEmailSortIcon()
			));

		var roleColumn = grid.addColumn(user -> user.getRole().name())
			.setAutoWidth(true).setFlexGrow(0)
			.setHeader(sortableHeader(i18n.get(USER_VIEW_HEADER_ROLE), queryBlock.getRoleSortIcon()));

		var createdColumn = grid.addColumn(user -> user.getCreatedAt().toString())
			.setAutoWidth(true).setFlexGrow(0)
			.setHeader(sortableHeader(i18n.get(USER_VIEW_HEADER_CREATED), queryBlock.getCreatedSortIcon()));

		var updatedColumn = grid.addColumn(user -> user.getUpdatedAt().toString())
			.setAutoWidth(true).setFlexGrow(0)
			.setHeader(sortableHeader(i18n.get(USER_VIEW_HEADER_UPDATED), queryBlock.getUpdatedSortIcon()));

		var actionsColumn = grid.addColumn(new ComponentRenderer<>(user -> {
				Button edit = new Button(VaadinIcon.EDIT.create());
				edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
				edit.addClickListener(e -> onEdit.accept(user));

				Button delete = new Button(VaadinIcon.TRASH.create());
				delete.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR);
				delete.addClickListener(e -> onDelete.accept(user));

				HorizontalLayout layout = new HorizontalLayout(edit, delete);
				layout.setSpacing(false);
				layout.setJustifyContentMode(JustifyContentMode.CENTER);
				return layout;
			}))
			.setHeader(i18n.get(USER_VIEW_HEADER_ACTIONS))
			.setAutoWidth(true)
			.setFlexGrow(0).setTextAlign(ColumnTextAlign.CENTER);

		HeaderRow filterRow = grid.appendHeaderRow();
		filterRow.getCell(idColumn).setComponent(queryBlock.getIdFilter());

		VerticalLayout nameAndEmailLayout = new VerticalLayout(queryBlock.getNameFilter(), queryBlock.getEmailFilter());
		nameAndEmailLayout.setSpacing(false);
		nameAndEmailLayout.setPadding(false);
		nameAndEmailLayout.setMargin(false);
		filterRow.getCell(nameAndEmailColumn).setComponent(nameAndEmailLayout);

		filterRow.getCell(roleColumn).setComponent(queryBlock.getRoleFilter());
		filterRow.getCell(createdColumn).setComponent(queryBlock.getCreatedFilter());
		filterRow.getCell(updatedColumn).setComponent(queryBlock.getUpdatedFilter());
		filterRow.getCell(actionsColumn).setComponent(queryBlock.getQueryActionBlock().getComponent());
	}

	private static Component sortableHeader(String label, Component sortIcon) {
		Span title = new Span(label);
		HorizontalLayout layout = new HorizontalLayout(title, sortIcon);
		layout.setAlignItems(Alignment.CENTER);
		return layout;
	}

	private static Component dualSortableHeader(String label1, Component sortIcon1,
												String label2, Component sortIcon2) {
		HorizontalLayout layout = new HorizontalLayout(
			new Span(label1), sortIcon1,
			new Span(" / "),
			new Span(label2), sortIcon2
		);
		layout.setAlignItems(Alignment.CENTER);
		return layout;
	}
}
