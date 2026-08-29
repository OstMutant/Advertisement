package org.ost.marketplace.ui.views.main.tabs.users;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.dto.UserFilterDto;
import org.ost.platform.user.dto.UserSettingsDto;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.orchestrator.services.UserDeleteService;
import org.ost.orchestrator.services.UserProfileService;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.views.components.PaginationBar;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.components.dialogs.ConfirmActionDialog;
import org.ost.marketplace.ui.query.QueryBlock;
import org.ost.marketplace.ui.query.QueryStatusBar;
import org.ost.marketplace.ui.views.main.header.account.AccountOverlay;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.marketplace.ui.views.services.pagination.SettingsPaginationBinding;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.views.utils.ValidationErrorUtil;

import java.util.ArrayList;
import java.util.List;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@Slf4j
@SpringComponent
@UIScope
@RequiredArgsConstructor
public class UserView extends VerticalLayout {

    private final transient UserProfileService                     userProfileService;
    private final transient UserDeleteService                      userDeleteService;
    private final transient AccessEvaluator                        access;
    private final transient I18nService                            i18n;
    private final transient NotificationService                    notificationService;
    private final QueryStatusBar<UserFilterDto>                    queryStatusBar;
    private final transient UiComponentFactory<UserGridConfigurator> gridConfiguratorFactory;
    private final AccountOverlay                                   overlay;
    private final PaginationBar                                    paginationBar;
    private final transient SettingsPaginationBinding              settingsPaginationBinding;

    private Grid<UserDto>  grid;
    private UiIconButton   refreshButton;
    private List<UserDto>  currentItems   = new ArrayList<>();
    private int            lastKnownTotal = 0;

    @PostConstruct
    protected void init() {
        addClassName("user-list-layout");
        setWidthFull();

        grid = new Grid<>(UserDto.class, false);
        grid.addClassName("user-grid");
        grid.setWidthFull();
        grid.setAllRowsVisible(true);

        paginationBar.addClassName("user-pagination");
        refreshButton = buildRefreshButton();

        VerticalLayout contentWrapper = new VerticalLayout(
                queryStatusBar, queryStatusBar.getQueryBlock(), refreshButton, grid, paginationBar
        );
        contentWrapper.setPadding(false);
        contentWrapper.setSpacing(false);
        contentWrapper.setWidthFull();

        add(contentWrapper);

        paginationBar.setPageChangeListener(_ -> refresh());
        queryStatusBar.getQueryBlock().addEventListener(() -> {
            paginationBar.setTotalCount(0);
            refresh();
        });
        gridConfiguratorFactory.build(
                UserGridConfigurator.Parameters.builder()
                        .grid(grid)
                        .onView(u -> overlay.openFor(u.id(), this::updateRowInPlace, this::checkForChanges))
                        .onEdit(u -> overlay.openForEdit(u.id(), this::updateRowInPlace, this::checkForChanges))
                        .onDelete(this::confirmAndDelete)
                        .build()
        );

        settingsPaginationBinding.register(paginationBar, UserSettingsDto::getUsersPageSize, this::refresh);
        refresh();
    }

    @PreDestroy
    public void destroy() {
        settingsPaginationBinding.unregister();
    }

    private void refresh() {
        int page = paginationBar.getCurrentPage();
        int size = paginationBar.getPageSize();

        QueryBlock<UserFilterDto> queryBlock = queryStatusBar.getQueryBlock();
        UserFilterDto currentFilter = queryBlock.getFilterProcessor().getOriginalFilter();
        var sort = queryBlock.getSortProcessor().getOriginalSort().getSort();

        try {
            List<UserDto> pageData   = userProfileService.getFiltered(currentFilter, page, size, sort);
            int           totalCount = userProfileService.count(currentFilter);
            paginationBar.setTotalCount(totalCount);
            lastKnownTotal = totalCount;
            refreshButton.setVisible(false);
            currentItems = new ArrayList<>(pageData);
            grid.setItems(currentItems);
        } catch (ConstraintViolationException ex) {
            log.warn("Validation error while fetching users: {}", ex.getMessage(), ex);
            showValidationErrors(ex);
            refreshButton.setVisible(false);
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

    private UiIconButton buildRefreshButton() {
        UiIconButton button = new UiIconButton(i18n.get(USER_VIEW_TOOLTIP_REFRESH_AVAILABLE), VaadinIcon.REFRESH.create());
        button.addClassName("user-refresh-button");
        button.setVisible(false);
        button.addClickListener(_ -> {
            paginationBar.resetToFirstPage();
            refresh();
        });
        return button;
    }

    private void updateRowInPlace(UserDto fresh) {
        for (int i = 0; i < currentItems.size(); i++) {
            if (currentItems.get(i).id().equals(fresh.id())) {
                currentItems.set(i, fresh);
                grid.setItems(currentItems);
                return;
            }
        }
    }

    private void checkForChanges() {
        UserFilterDto filter = queryStatusBar.getQueryBlock().getFilterProcessor().getOriginalFilter();
        int currentTotal;
        try {
            currentTotal = userProfileService.count(filter);
        } catch (Exception _) {
            return;
        }
        refreshButton.setVisible(currentTotal != lastKnownTotal);
    }

    private void confirmAndDelete(UserDto user) {
        new ConfirmActionDialog(
                i18n.get(USER_VIEW_CONFIRM_DELETE_TITLE),
                i18n.get(USER_VIEW_CONFIRM_DELETE_TEXT, user.name(), user.id()),
                i18n.get(USER_VIEW_CONFIRM_DELETE_BUTTON),
                i18n.get(USER_VIEW_CONFIRM_CANCEL_BUTTON),
                () -> {
                    try {
                        if (access.canNotDelete(user.id())) return;
                        userDeleteService.delete(user.id(), access.getCurrentUserId());
                        notificationService.success(USER_VIEW_NOTIFICATION_DELETED);
                        refresh();
                    } catch (@SuppressWarnings("java:S7467") Exception e) {
                        log.error("Error deleting user id={}", user.id(), e);
                        notificationService.error(USER_VIEW_NOTIFICATION_DELETE_ERROR, e.getMessage());
                    }
                }
        ).open();
    }

    private void showValidationErrors(ConstraintViolationException ex) {
        notificationService.error(i18n.get(USER_VIEW_NOTIFICATION_VALIDATION_FAILED) + "\n" + ValidationErrorUtil.buildMessage(ex));
    }
}
