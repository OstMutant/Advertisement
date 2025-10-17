package org.ost.advertisement.ui.views.users;

import static org.ost.advertisement.constans.I18nKey.USER_VIEW_CONFIRM_CANCEL_BUTTON;
import static org.ost.advertisement.constans.I18nKey.USER_VIEW_CONFIRM_DELETE_BUTTON;
import static org.ost.advertisement.constans.I18nKey.USER_VIEW_CONFIRM_DELETE_TEXT;
import static org.ost.advertisement.constans.I18nKey.USER_VIEW_NOTIFICATION_DELETED;
import static org.ost.advertisement.constans.I18nKey.USER_VIEW_NOTIFICATION_DELETE_ERROR;
import static org.ost.advertisement.constans.I18nKey.USER_VIEW_NOTIFICATION_VALIDATION_FAILED;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.ost.advertisement.dto.filter.UserFilterDto;
import org.ost.advertisement.dto.sort.CustomSort;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.UserService;
import org.ost.advertisement.ui.mappers.UserMapper;
import org.ost.advertisement.ui.utils.NotificationType;
import org.ost.advertisement.ui.views.components.dialogs.ConfirmDeleteHelper;

@SpringComponent
@UIScope
public class UserView extends UserLayout {

	private final transient UserService userService;
	private final transient UserMapper mapper;
	private final transient I18nService i18n;
	private final transient UserFilterFields filterFields;
	private final transient CustomSort customSort = new CustomSort();

	public UserView(UserFilterFields filterFields, UserService userService, I18nService i18n, UserMapper mapper) {
		super(i18n);
		this.filterFields = filterFields;
		this.userService = userService;
		this.i18n = i18n;
		this.mapper = mapper;

		getPaginationBar().setPageChangeListener(event -> refreshGrid());

		filterFields.eventProcessor(() -> {
			getPaginationBar().setTotalCount(0);
			refreshGrid();
		});

		UserGridConfigurator.configure(
			getGrid(),
			filterFields,
			i18n,
			customSort,
			this::openUserFormDialog,
			this::confirmAndDelete,
			this::refreshGrid
		);

		refreshGrid();
	}

	private void refreshGrid() {
		int page = getPaginationBar().getCurrentPage();
		int size = getPaginationBar().getPageSize();
		UserFilterDto currentFilter = filterFields.getFilterFieldsProcessor().getNewFilter();
		try {
			List<User> pageData = userService.getFiltered(currentFilter, page, size, customSort.getSort());
			int totalCount = userService.count(currentFilter);
			getPaginationBar().setTotalCount(totalCount);
			getGrid().setItems(pageData);
		} catch (ConstraintViolationException ex) {
			showValidationErrors(ex);
			getGrid().setItems(List.of());
			getPaginationBar().setTotalCount(0);
		}
	}

	private void openUserFormDialog(User user) {
		UserFormDialog dialog = new UserFormDialog(mapper.toUserEdit(user), userService, i18n, mapper);
		dialog.addOpenedChangeListener(e -> {
			if (!e.isOpened()) {
				refreshGrid();
			}
		});
		dialog.open();
	}

	private void confirmAndDelete(User user) {
		ConfirmDeleteHelper.showConfirm(i18n,
			i18n.get(USER_VIEW_CONFIRM_DELETE_TEXT, user.name(), user.getId()),
			USER_VIEW_CONFIRM_DELETE_BUTTON,
			USER_VIEW_CONFIRM_CANCEL_BUTTON,
			() -> {
				try {
					userService.delete(user);
					NotificationType.SUCCESS.show(i18n.get(USER_VIEW_NOTIFICATION_DELETED));
					refreshGrid();
				} catch (Exception ex) {
					NotificationType.ERROR.show(i18n.get(USER_VIEW_NOTIFICATION_DELETE_ERROR, ex.getMessage()));
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
