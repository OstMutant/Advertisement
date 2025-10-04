package org.ost.advertisement.ui.views.components.dialogs;

import static org.ost.advertisement.ui.utils.TimeZoneUtil.formatInstant;

import com.vaadin.flow.data.binder.Binder;
import java.time.Instant;
import org.ost.advertisement.services.I18nService;

public abstract class GenericFormDialog<T> extends DialogForm {

	protected final transient T dto;
	protected final Binder<T> binder;

	protected GenericFormDialog(T dto, Class<T> clazz, I18nService i18n) {
		super(i18n);
		this.dto = dto;
		this.binder = new Binder<>(clazz);
		this.binder.setBean(dto);
	}

	protected void save(Saver<T> saver, String successKey, String errorKey) {
		try {
			binder.writeBean(dto);
			saver.save(dto);
			showSuccess(successKey);
			close();
		} catch (Exception e) {
			showError(errorKey, e.getMessage());
		}
	}

	protected void showSuccess(String i18nKey) {
		showNotification(i18n.get(i18nKey), "success");
	}

	protected void showError(String i18nKey, String details) {
		showNotification(i18n.get(i18nKey, details), "error");
	}

	protected String formatDate(Instant instant) {
		return formatInstant(instant, "—");
	}

	private void showNotification(String text, String variant) {
		var notification = com.vaadin.flow.component.notification.Notification.show(text, 4000,
			com.vaadin.flow.component.notification.Notification.Position.BOTTOM_START);
		notification.addThemeVariants(
			"success".equals(variant)
				? com.vaadin.flow.component.notification.NotificationVariant.LUMO_SUCCESS
				: com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR
		);
	}

	@FunctionalInterface
	public interface Saver<T> {

		void save(T dto);
	}
}
