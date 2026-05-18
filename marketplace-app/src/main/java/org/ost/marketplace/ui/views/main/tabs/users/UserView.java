package org.ost.marketplace.ui.views.main.tabs.users;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.marketplace.dto.filter.UserFilterDto;
import org.ost.marketplace.entities.User;
import org.ost.platform.core.config.UserSettings;
import org.ost.platform.core.i18n.I18nService;
import org.ost.marketplace.services.user.UserService;
import org.ost.marketplace.ui.views.components.PaginationBar;
import org.ost.marketplace.ui.views.components.dialogs.ConfirmActionDialog;
import org.ost.marketplace.ui.views.components.query.QueryBlock;
import org.ost.marketplace.ui.views.components.query.QueryStatusBar;
import org.ost.marketplace.ui.views.main.tabs.users.overlay.UserOverlay;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.marketplace.ui.views.services.pagination.SettingsPaginationBinding;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.ost.marketplace.common.I18nKey.*;

@Slf4j
@SpringComponent
@UIScope
@RequiredArgsConstructor
public class UserView extends VerticalLayout {

    private final transient UserService                  userService;
    private final transient I18nService                  i18n;
    private final transient NotificationService          notificationService;
    private final QueryStatusBar<UserFilterDto>          queryStatusBar;
    private final transient UserGridConfigurator.Builder gridConfiguratorBuilder;
    private final UserOverlay                            overlay;
    private final transient ConfirmActionDialog.Builder  confirmActionDialogBuilder;
    private final PaginationBar                          paginationBar;
    private final transient SettingsPaginationBinding    settingsPaginationBinding;

    private Grid<User> grid;

    @PostConstruct
    protected void init() {
        addClassName("user-list-layout");
        setWidthFull();

        grid = new Grid<>(User.class, false);
        grid.addClassName("user-grid");
        grid.setWidthFull();
        grid.setAllRowsVisible(true);

        paginationBar.addClassName("user-pagination");

        VerticalLayout contentWrapper = new VerticalLayout(
                queryStatusBar, queryStatusBar.getQueryBlock(), grid, paginationBar
        );
        contentWrapper.setPadding(false);
        contentWrapper.setSpacing(false);
        contentWrapper.setWidthFull();

        add(contentWrapper, overlay);

        initPagination();
        initQueryBar();
        initGrid();

        settingsPaginationBinding.register(paginationBar, UserSettings::getUsersPageSize, this::refreshGrid);
        refreshGrid();
    }

    @PreDestroy
    public void destroy() {
        settingsPaginationBinding.unregister();
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
        gridConfiguratorBuilder.build(
                UserGridConfigurator.Parameters.builder()
                        .grid(grid)
                        .onView(u -> overlay.openForView(u, this::refreshGrid))
                        .onEdit(u -> overlay.openForEdit(u, this::refreshGrid))
                        .onDelete(this::confirmAndDelete)
                        .build()
        );
    }

    private void refreshGrid() {
        int page = paginationBar.getCurrentPage();
        int size = paginationBar.getPageSize();

        QueryBlock<UserFilterDto> queryBlock = queryStatusBar.getQueryBlock();
        UserFilterDto currentFilter = queryBlock.getFilterProcessor().getOriginalFilter();
        var sort = queryBlock.getSortProcessor().getOriginalSort().getSort();

        try {
            List<User> pageData   = userService.getFiltered(currentFilter, page, size, sort);
            int        totalCount = userService.count(currentFilter);
            paginationBar.setTotalCount(totalCount);
            grid.setItems(pageData);
        } catch (ConstraintViolationException ex) {
            log.warn("Validation error while fetching users: {}", ex.getMessage(), ex);
            showValidationErrors(ex);
            grid.setItems(List.of());
            paginationBar.setTotalCount(0);
        } catch (Exception ex) {
            log.error("Unexpected error while refreshing user grid", ex);
            notificationService.error(USER_VIEW_NOTIFICATION_DELETE_ERROR, ex.getMessage());
            grid.setItems(List.of());
            paginationBar.setTotalCount(0);
        } finally {
            queryStatusBar.update();
        }
    }

    private void confirmAndDelete(User user) {
        confirmActionDialogBuilder.build(
                ConfirmActionDialog.Parameters.builder()
                        .titleKey(USER_VIEW_CONFIRM_DELETE_TITLE)
                        .message(i18n.get(USER_VIEW_CONFIRM_DELETE_TEXT, user.getName(), user.getId()))
                        .confirmKey(USER_VIEW_CONFIRM_DELETE_BUTTON)
                        .cancelKey(USER_VIEW_CONFIRM_CANCEL_BUTTON)
                        .onConfirm(() -> {
                            try {
                                userService.delete(user);
                                notificationService.success(USER_VIEW_NOTIFICATION_DELETED);
                                refreshGrid();
                            } catch (Exception e) {
                                log.error("Error deleting user id={}", user.getId(), e);
                                notificationService.error(USER_VIEW_NOTIFICATION_DELETE_ERROR, e.getMessage());
                            }
                        })
                        .build()
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
