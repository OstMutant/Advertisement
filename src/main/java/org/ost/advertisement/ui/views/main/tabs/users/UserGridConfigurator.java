package org.ost.advertisement.ui.views.main.tabs.users;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.*;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.utils.TimeZoneUtil;
import org.ost.advertisement.ui.views.rules.Configurable;
import org.ost.advertisement.ui.views.rules.ComponentBuilder;
import org.ost.advertisement.ui.views.components.buttons.action.DeleteActionButton;
import org.ost.advertisement.ui.views.components.buttons.action.EditActionButton;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import java.util.function.Consumer;

import static com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER;
import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class UserGridConfigurator implements Configurable<UserGridConfigurator, UserGridConfigurator.Parameters> {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull Grid<User>     grid;
        @NonNull Consumer<User> onView;
        @NonNull Consumer<User> onEdit;
        @NonNull Consumer<User> onDelete;
    }

    private final I18nService               i18n;
    private final EditActionButton.Builder  editButtonBuilder;
    private final DeleteActionButton.Builder deleteButtonBuilder;

    @Override
    public UserGridConfigurator configure(Parameters p) {
        Grid<User> grid = p.getGrid();

        grid.setSizeFull();
        grid.addItemClickListener(e -> p.getOnView().accept(e.getItem()));

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
                            EditActionButton.Parameters.builder()
                                    .tooltip(i18n.get(USER_VIEW_BUTTON_EDIT))
                                    .onClick(() -> p.getOnEdit().accept(user))
                                    .build()
                    );
                    Button delete = deleteButtonBuilder.build(
                            DeleteActionButton.Parameters.builder()
                                    .tooltip(i18n.get(USER_VIEW_BUTTON_DELETE))
                                    .onClick(() -> p.getOnDelete().accept(user))
                                    .build()
                    );
                    HorizontalLayout layout = new HorizontalLayout(edit, delete);
                    layout.addClassName("user-grid-actions");
                    return layout;
                }))
                .setHeader(getHeader(i18n.get(USER_VIEW_HEADER_ACTIONS)))
                .setAutoWidth(true)
                .setFlexGrow(0).setTextAlign(ColumnTextAlign.CENTER);

        return this;
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

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder extends ComponentBuilder<UserGridConfigurator, Parameters> {
        @Getter
        private final ObjectProvider<UserGridConfigurator> provider;
    }
}