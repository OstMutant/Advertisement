package org.ost.advertisement.ui.views.components.dialogs;

import static org.ost.advertisement.ui.views.components.dialogs.DialogContentFactory.showError;
import static org.ost.advertisement.ui.views.components.dialogs.DialogContentFactory.showSuccess;

import com.vaadin.flow.data.binder.Binder;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.constans.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.TimeZoneUtil;

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
			showSuccess(i18n, successKey);
			close();
		} catch (Exception e) {
			log.error("Failed to save {}", dto, e);
			showError(i18n, errorKey, e.getMessage());
		}
	}

	protected String formatDate(Instant instant) {
		return TimeZoneUtil.formatInstant(instant, "—");
	}

	@FunctionalInterface
	public interface Saver<T> {

		void save(T dto);
	}
}

