package org.ost.advertisement.ui.views.users;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.dto.filter.UserFilterDto;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.UserService;
import org.ost.advertisement.ui.services.NotificationService;
import org.ost.advertisement.ui.views.components.PaginationBarModern;
import org.ost.advertisement.ui.views.components.buttons.DeleteActionButton;
import org.ost.advertisement.ui.views.components.buttons.EditActionButton;
import org.ost.advertisement.ui.views.components.dialogs.ConfirmDeleteDialog;
import org.ost.advertisement.ui.views.users.overlay.UserOverlay;
import org.ost.advertisement.ui.views.users.query.elements.UserQueryStatusBar;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.ost.advertisement.constants.I18nKey.*;

@Slf4j
@SpringComponent
@UIScope
@RequiredArgsConstructor
public class UserView extends VerticalLayout {

    private final transient UserService               userService;
    private final transient I18nService               i18n;
    private final transient NotificationService       notificationService;
    private final UserQueryStatusBar                  queryStatusBar;
    private final transient EditActionButton.Builder  editButtonBuilder;
    private final transient DeleteActionButton.Builder deleteButtonBuilder;
    private final UserOverlay                         overlay;
    private final transient ConfirmDeleteDialog.Builder confirmDeleteDialogBuilder;

    private Grid<User>         grid;
    private PaginationBarModern paginationBar;

    @PostConstruct
    protected void init() {
        addClassName("user-list-layout");
        setWidthFull();

        grid = new Grid<>(User.class, false);
        grid.addClassName("user-grid");
        grid.setWidthFull();
        grid.setAllRowsVisible(true);

        paginationBar = new PaginationBarModern(i18n);
        paginationBar.addClassName("user-pagination");

        add(grid, paginationBar, overlay); // ← додати overlay

        initPagination();
        initQueryBar();
        initGrid();

        addComponentAsFirst(queryStatusBar);
        addComponentAtIndex(1, queryStatusBar.getQueryBlock());

        refreshGrid();
    }

    private void initPagination() {
        paginationBar.setPageChangeListener(_ -> refreshGrid());
    }

    private void initQueryBar() {
        queryStatusBar.getQueryBlock().addEventListener(() -> {
            paginationBar.setTotalCount(0);
            refreshGrid();
        });
    }

    private void initGrid() {
        UserGridConfigurator.configure(
                grid,
                i18n,
                editButtonBuilder,
                deleteButtonBuilder,
                u -> overlay.openForView(u, this::refreshGrid),
                u -> overlay.openForEdit(u, this::refreshGrid),
                this::confirmAndDelete
        );
    }

    private void refreshGrid() {
        int page = paginationBar.getCurrentPage();
        int size = paginationBar.getPageSize();

        UserFilterDto currentFilter = queryStatusBar.getQueryBlock().getFilterProcessor().getNewFilter();
        var sort = queryStatusBar.getQueryBlock().getSortProcessor().getOriginalSort().getSort();

        try {
            List<User> pageData = userService.getFiltered(currentFilter, page, size, sort);
            int totalCount = userService.count(currentFilter);
            paginationBar.setTotalCount(totalCount);
            grid.setItems(pageData != null ? pageData : Collections.emptyList());
        } catch (ConstraintViolationException ex) {
            log.warn("Validation error while fetching users: {}", ex.getMessage(), ex);
            showValidationErrors(ex);
            grid.setItems(Collections.emptyList());
            paginationBar.setTotalCount(0);
        } catch (Exception ex) {
            log.error("Unexpected error while refreshing user grid", ex);
            notificationService.error(USER_VIEW_NOTIFICATION_DELETE_ERROR, ex.getMessage());
            grid.setItems(Collections.emptyList());
            paginationBar.setTotalCount(0);
        } finally {
            queryStatusBar.update();
        }
    }

    private void confirmAndDelete(User user) {
        confirmDeleteDialogBuilder.build(
                USER_VIEW_CONFIRM_DELETE_TITLE,
                i18n.get(USER_VIEW_CONFIRM_DELETE_TEXT, user.getName(), user.getId()),
                USER_VIEW_CONFIRM_DELETE_BUTTON,
                USER_VIEW_CONFIRM_CANCEL_BUTTON,
                () -> {
                    try {
                        userService.delete(user);
                        notificationService.success(USER_VIEW_NOTIFICATION_DELETED);
                        refreshGrid();
                    } catch (Exception e) {
                        log.error("Error deleting user id={}", user.getId(), e);
                        notificationService.error(USER_VIEW_NOTIFICATION_DELETE_ERROR, e.getMessage());
                    }
                }
        ).open();
    }

    private void showValidationErrors(ConstraintViolationException ex) {
        Set<ConstraintViolation<?>> violations = ex.getConstraintViolations();
        String message = violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .distinct()
                .sorted()
                .collect(Collectors.joining("\n"));
        notificationService.error(i18n.get(USER_VIEW_NOTIFICATION_VALIDATION_FAILED) + "\n" + message);
    }
}
