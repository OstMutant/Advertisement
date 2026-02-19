package org.ost.advertisement.ui.views.users;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.TimeZoneUtil;
import org.ost.advertisement.ui.views.components.buttons.DeleteActionButton;
import org.ost.advertisement.ui.views.components.buttons.EditActionButton;

import java.util.function.Consumer;

import static com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER;
import static org.ost.advertisement.constants.I18nKey.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserGridConfigurator {

    public static void configure(Grid<User> grid,
                                 I18nService i18n,
                                 EditActionButton.Builder editButtonBuilder,
                                 DeleteActionButton.Builder deleteButtonBuilder,
                                 Consumer<User> onView,
                                 Consumer<User> onEdit,
                                 Consumer<User> onDelete) {

        grid.setSizeFull();

        grid.addItemClickListener(e -> onView.accept(e.getItem()));

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
                    Button edit = editButtonBuilder.build(
                            EditActionButton.Config.builder()
                                    .tooltip(i18n.get(USER_VIEW_BUTTON_EDIT))
                                    .onClick(() -> onEdit.accept(user))
                                    .build()
                    );

                    Button delete = deleteButtonBuilder.build(
                            DeleteActionButton.Config.builder()
                                    .tooltip(i18n.get(USER_VIEW_BUTTON_DELETE))
                                    .onClick(() -> onDelete.accept(user))
                                    .build()
                    );

                    HorizontalLayout layout = new HorizontalLayout(edit, delete);
                    layout.addClassName("user-grid-actions");
                    return layout;
                }))
                .setHeader(getHeader(i18n.get(USER_VIEW_HEADER_ACTIONS)))
                .setAutoWidth(true)
                .setFlexGrow(0).setTextAlign(ColumnTextAlign.CENTER);
    }

    private static Component getHeader(String label) {
        Span span = new Span(label);
        span.addClassName("user-grid-header");
        return span;
    }

    private static Component getDualHeader(String label1, String label2) {
        HorizontalLayout layout = new HorizontalLayout(new Span(label1), new Span(" / "), new Span(label2));
        layout.setAlignItems(CENTER);
        layout.addClassName("user-grid-header");
        return layout;
    }
}