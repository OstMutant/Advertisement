package org.ost.advertisement.ui.views.users;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.ost.advertisement.dto.filter.UserFilterDto;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.UserService;
import org.ost.advertisement.ui.utils.NotificationType;
import org.ost.advertisement.ui.views.components.dialogs.ConfirmDeleteHelper;
import org.ost.advertisement.ui.views.users.dialogs.UserEditDialog;
import org.ost.advertisement.ui.views.users.dialogs.UserViewDialog;
import org.ost.advertisement.ui.views.users.query.elements.UserQueryStatusBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@UIScope
public class UserView extends UserLayout {

    private static final Logger log = LoggerFactory.getLogger(UserView.class);

    private final transient UserService userService;
    private final transient I18nService i18n;
    private final UserQueryStatusBar queryStatusBar;

    public UserView(UserQueryStatusBar queryStatusBar,
                    UserService userService,
                    I18nService i18n,
                    UserEditDialog.Builder editDialogBuilder) {
        super(i18n);
        this.queryStatusBar = queryStatusBar;
        this.userService = userService;
        this.i18n = i18n;

        initPagination();
        initQueryBar();
        initGrid(editDialogBuilder);

        addComponentAsFirst(queryStatusBar);
        addComponentAtIndex(1, queryStatusBar.getQueryBlock());

        refreshGrid();
    }

    private void initPagination() {
        getPaginationBar().setPageChangeListener(_ -> refreshGrid());
    }

    private void initQueryBar() {
        queryStatusBar.getQueryBlock().addEventListener(() -> {
            getPaginationBar().setTotalCount(0);
            refreshGrid();
        });
    }

    private void initGrid(UserEditDialog.Builder editDialogBuilder) {
        UserGridConfigurator.configure(
                getGrid(),
                i18n,
                this::openViewDialog,
                u -> editDialogBuilder.buildAndOpen(u, this::refreshGrid),
                this::confirmAndDelete
        );
    }

    private void openViewDialog(User user) {
        UserViewDialog dialog = new UserViewDialog(i18n, user);
        dialog.addOpenedChangeListener(event -> {
            if (!event.isOpened()) {
                getElement().executeJs("document.activeElement.blur()");
            }
        });
        dialog.open();
    }

    private void refreshGrid() {
        int page = getPaginationBar().getCurrentPage();
        int size = getPaginationBar().getPageSize();

        UserFilterDto currentFilter = queryStatusBar.getQueryBlock().getFilterProcessor().getNewFilter();
        var sort = queryStatusBar.getQueryBlock().getSortProcessor().getOriginalSort().getSort();

        try {
            List<User> pageData = userService.getFiltered(currentFilter, page, size, sort);
            int totalCount = userService.count(currentFilter);
            getPaginationBar().setTotalCount(totalCount);
            getGrid().setItems(pageData != null ? pageData : Collections.emptyList());
        } catch (ConstraintViolationException ex) {
            log.warn("Validation error while fetching users: {}", ex.getMessage(), ex);
            showValidationErrors(ex);
            getGrid().setItems(Collections.emptyList());
            getPaginationBar().setTotalCount(0);
        } catch (Exception ex) {
            log.error("Unexpected error while refreshing user grid", ex);
            NotificationType.ERROR.show(i18n.get(USER_VIEW_NOTIFICATION_DELETE_ERROR, ex.getMessage()));
            getGrid().setItems(Collections.emptyList());
            getPaginationBar().setTotalCount(0);
        } finally {
            queryStatusBar.update();
        }
    }

    private void confirmAndDelete(User user) {
        ConfirmDeleteHelper.showConfirm(
                i18n,
                i18n.get(USER_VIEW_CONFIRM_DELETE_TEXT, user.getName(), user.getId()),
                USER_VIEW_CONFIRM_DELETE_BUTTON,
                USER_VIEW_CONFIRM_CANCEL_BUTTON,
                () -> {
                    try {
                        userService.delete(user);
                        NotificationType.SUCCESS.show(i18n.get(USER_VIEW_NOTIFICATION_DELETED));
                        refreshGrid();
                    } catch (Exception ex) {
                        log.error("Error deleting user id={}", user.getId(), ex);
                        NotificationType.ERROR.show(
                                i18n.get(USER_VIEW_NOTIFICATION_DELETE_ERROR, ex.getMessage())
                        );
                    }
                }
        );
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