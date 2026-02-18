package org.ost.advertisement.ui.views.users;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.TimeZoneUtil;

import java.util.function.Consumer;

import static com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER;
import static org.ost.advertisement.constants.I18nKey.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserGridConfigurator {

    public static void configure(Grid<User> grid,
                                 I18nService i18n,
                                 Consumer<User> onEdit,
                                 Consumer<User> onDelete) {

        grid.setSizeFull();

        grid.addItemClickListener(e -> onEdit.accept(e.getItem()));

        grid.addColumn(User::getId)
                .setAutoWidth(true).setFlexGrow(0).setTextAlign(ColumnTextAlign.END)
                .setHeader(getHeader(i18n.get(USER_VIEW_HEADER_ID)));

        grid.addColumn(new ComponentRenderer<>(user -> {
                    Span nameSpan = new Span(user.getName());
                    nameSpan.addClassName("user-grid-name");

                    Span emailSpan = new Span(user.getEmail());
                    emailSpan.addClassName("user-grid-email");

                    VerticalLayout layout = new VerticalLayout(nameSpan, emailSpan);
                    layout.setSpacing(false);
                    layout.setPadding(false);
                    layout.setMargin(false);
                    return layout;
                }))
                .setFlexGrow(1)
                .setHeader(getDualHeader(i18n.get(USER_VIEW_HEADER_NAME), i18n.get(USER_VIEW_HEADER_EMAIL)));

        grid.addColumn(user -> user.getRole().name())
                .setAutoWidth(true).setFlexGrow(0)
                .setHeader(getHeader(i18n.get(USER_VIEW_HEADER_ROLE)));

        grid.addColumn(user -> TimeZoneUtil.formatInstantHuman(user.getCreatedAt()))
                .setAutoWidth(true).setFlexGrow(0)
                .setHeader(getHeader(i18n.get(USER_VIEW_HEADER_CREATED)));

        grid.addColumn(user -> TimeZoneUtil.formatInstantHuman(user.getUpdatedAt()))
                .setAutoWidth(true).setFlexGrow(0)
                .setHeader(getHeader(i18n.get(USER_VIEW_HEADER_UPDATED)));

        grid.addColumn(new ComponentRenderer<>(user -> {
                    Button edit = new Button(VaadinIcon.EDIT.create());
                    edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
                    edit.getElement().setAttribute("title", i18n.get(USER_VIEW_BUTTON_EDIT));
                    edit.addClickListener(e -> e.getSource().getUI().ifPresent(_ -> onEdit.accept(user)));

                    Button delete = new Button(VaadinIcon.TRASH.create());
                    delete.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR);
                    delete.getElement().setAttribute("title", i18n.get(USER_VIEW_BUTTON_DELETE));
                    delete.addClickListener(e -> e.getSource().getUI().ifPresent(_ -> onDelete.accept(user)));

                    HorizontalLayout layout = new HorizontalLayout(edit, delete);
                    layout.addClassName("user-grid-actions");
                    return layout;
                }))
                .setHeader(i18n.get(USER_VIEW_HEADER_ACTIONS))
                .setAutoWidth(true)
                .setFlexGrow(0).setTextAlign(ColumnTextAlign.CENTER);
    }

    private static Component getHeader(String label) {
        return new Span(label);
    }

    private static Component getDualHeader(String label1, String label2) {
        HorizontalLayout layout = new HorizontalLayout(new Span(label1), new Span(" / "), new Span(label2));
        layout.setAlignItems(CENTER);
        return layout;
    }
}
