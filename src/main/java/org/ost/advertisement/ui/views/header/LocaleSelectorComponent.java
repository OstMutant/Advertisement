package org.ost.advertisement.ui.views.header;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.security.utils.AuthUtil;
import org.ost.advertisement.services.UserService;
import org.ost.advertisement.ui.utils.SessionUtil;

@SpringComponent
@UIScope
public class LocaleSelectorComponent extends HorizontalLayout {

	private static final List<LocaleWrapper> LOCALES = List.of(
		new LocaleWrapper("English", Locale.of("en")),
		new LocaleWrapper("Українська", Locale.of("uk"))
	);

	public LocaleSelectorComponent(UserService userService) {
		setSpacing(true);
		setPadding(false);
		setMargin(false);

		ComboBox<LocaleWrapper> localeSelect = new ComboBox<>();
		localeSelect.setItems(LOCALES);
		localeSelect.setItemLabelGenerator(LocaleWrapper::label);
		localeSelect.setWidth("150px");

		LocaleWrapper selected = LOCALES.stream()
			.filter(wrapper -> wrapper.locale().getLanguage().equals(SessionUtil.getCurrentLocale().getLanguage()))
			.findFirst()
			.orElse(LOCALES.getFirst());

		localeSelect.setValue(selected);
		add(localeSelect);

		localeSelect.addValueChangeListener(event -> {
			Locale newLocale = event.getValue().locale();
			User currentUser = AuthUtil.getCurrentUser();
			if (currentUser != null) {
				currentUser = currentUser.withLocale(newLocale);
				userService.save(currentUser);
				AuthUtil.updateCurrentUser(currentUser);
			} else {
				UI.getCurrent().getSession().setLocale(newLocale);
			}
			SessionUtil.refreshCurrentLocale();
			UI.getCurrent().getPage().reload();
		});
	}

	private record LocaleWrapper(String label, Locale locale) {

		@NotNull
		@Override
		public String toString() {
			return label;
		}
	}
}
