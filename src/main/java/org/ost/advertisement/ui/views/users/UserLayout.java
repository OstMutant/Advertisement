package org.ost.advertisement.ui.views.users;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import lombok.Getter;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.PaginationBarModern;

@Getter
public abstract class UserLayout extends VerticalLayout {

    public abstract I18nService getI18n();

    private final Grid<User> grid = new Grid<>(User.class, false);
    private final PaginationBarModern paginationBar = new PaginationBarModern(getI18n());

    protected void init() {

        addClassName("user-list-layout");

        setWidthFull();

        grid.addClassName("user-grid");
        grid.setWidthFull();
        grid.setAllRowsVisible(true);

        paginationBar.addClassName("user-pagination");

        add(grid, paginationBar);
    }
}
