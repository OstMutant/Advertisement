package org.ost.advertisement.ui.views.components.dialogs;

import com.vaadin.flow.data.binder.Binder;
import java.time.Instant;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.NotificationType;

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
		NotificationType.SUCCESS.show(i18n.get(i18nKey));
	}

	protected void showError(String i18nKey, String details) {
		NotificationType.ERROR.show(i18n.get(i18nKey, details));
	}

	protected String formatDate(Instant instant) {
		return org.ost.advertisement.ui.utils.TimeZoneUtil.formatInstant(instant, "—");
	}

	@FunctionalInterface
	public interface Saver<T> {
		void save(T dto);
	}
}
