package org.ost.advertisement.ui.views.users;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import lombok.Getter;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.PaginationBarModern;

@Getter
public class UserLayout extends VerticalLayout {

    private final Grid<User> grid = new Grid<>(User.class, false);
    private final PaginationBarModern paginationBar;

    public UserLayout(I18nService i18n) {
        this.paginationBar = new PaginationBarModern(i18n);

        // CSS class names only; visual rules moved to CSS
        addClassName("user-list-layout");

        // keep width behavior in code or CSS (both ok)
        setWidthFull();

        grid.addClassName("user-grid");
        grid.setWidthFull();
        grid.setAllRowsVisible(true);

        paginationBar.addClassName("user-pagination");

        add(grid, paginationBar);
    }
}
