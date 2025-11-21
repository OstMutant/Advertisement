package org.ost.advertisement.ui.views.components.dialogs;

import static org.ost.advertisement.ui.views.components.dialogs.DialogContentFactory.showError;
import static org.ost.advertisement.ui.views.components.dialogs.DialogContentFactory.showSuccess;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

@Slf4j
@SpringComponent
@Scope("prototype")
public class FormDialogDelegate<T> {

	private final transient I18nService i18n;
	private final DialogLayout layout = new DialogLayout();
	private final Dialog dialog = new Dialog();
	@Getter
	private final T dto;
	@Getter
	private final Binder<T> binder;

	private FormDialogDelegate(Class<T> clazz, I18nService i18n, T dto, Runnable refresh) {
		this.i18n = i18n;
		this.dto = dto;
		this.binder = new Binder<>(clazz);
		this.binder.setBean(dto);

		DialogStyle.apply(dialog, "");
		dialog.add(layout.getLayout());

		if (refresh != null) {
			dialog.addOpenedChangeListener(event -> {
				if (!event.isOpened()) {
					refresh.run();
				}
			});
		}
	}

	public void setTitle(String header) {
		layout.setHeader(header);
	}

	public void addContent(Component... components) {
		layout.addFormContent(components);
	}

	public void addActions(Component... components) {
		layout.addActions(components);
	}

	public void save(Saver<T> saver, I18nKey successKey, I18nKey errorKey) {
		if (dto == null) {
			log.error("DTO is null, cannot save");
			showError(i18n, errorKey, "DTO is not initialized");
			return;
		}
		if (binder.writeBeanIfValid(dto)) {
			saver.save(dto);
			showSuccess(i18n, successKey);
			dialog.close();
		} else {
			showError(i18n, errorKey, "Validation failed");
		}
	}

	public void open() {
		dialog.open();
	}

	public void close() {
		dialog.close();
	}

	@FunctionalInterface
	public interface Saver<T> {

		void save(T dto);
	}

	@SpringComponent
	public static class Builder<T> {

		private final I18nService i18n;
		private final ObjectProvider<FormDialogDelegate<T>> delegateProvider;

		private Class<T> clazz;
		private T dto;
		private Runnable refresh;

		public Builder(I18nService i18n, ObjectProvider<FormDialogDelegate<T>> delegateProvider) {
			this.i18n = i18n;
			this.delegateProvider = delegateProvider;
		}

		public Builder<T> withClass(Class<T> clazz) {
			this.clazz = clazz;
			return this;
		}

		public Builder<T> withDto(T dto) {
			this.dto = dto;
			return this;
		}

		public Builder<T> withRefresh(Runnable refresh) {
			this.refresh = refresh;
			return this;
		}

		public FormDialogDelegate<T> build() {
			if (clazz == null) {
				throw new IllegalStateException("Class<T> must be provided");
			}
			if (dto == null) {
				throw new IllegalStateException("DTO must be provided");
			}
			return delegateProvider.getObject(clazz, i18n, dto, refresh);
		}
	}
}
