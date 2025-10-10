package org.ost.advertisement.ui.views.components.dialogs;

import com.vaadin.flow.data.binder.Binder;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.constans.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.NotificationType;

@Slf4j
public abstract class GenericFormDialog<T> extends DialogForm {

	protected final transient T dto;
	protected final Binder<T> binder;

	protected GenericFormDialog(T dto, Class<T> clazz, I18nService i18n) {
		super(i18n);
		this.dto = dto;
		this.binder = new Binder<>(clazz);
		this.binder.setBean(dto);
	}

	protected void save(Saver<T> saver, I18nKey successKey, I18nKey errorKey) {
		try {
			binder.writeBean(dto);
			saver.save(dto);
			showSuccess(successKey);
			close();
		} catch (Exception e) {
			log.error("Failed to save {}", dto, e);
			showError(errorKey, e.getMessage());
		}
	}

	protected void showSuccess(I18nKey key) {
		NotificationType.SUCCESS.show(i18n.get(key));
	}

	protected void showError(I18nKey key, String details) {
		NotificationType.ERROR.show(i18n.get(key, details));
	}

	protected String formatDate(Instant instant) {
		return org.ost.advertisement.ui.utils.TimeZoneUtil.formatInstant(instant, "—");
	}

	@FunctionalInterface
	public interface Saver<T> {

		void save(T dto);
	}
}

