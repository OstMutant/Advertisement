package org.ost.advertisement.ui.views.header;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.jetbrains.annotations.NotNull;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.security.utils.AuthUtil;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.UserService;
import org.ost.advertisement.ui.utils.SessionUtil;

import java.util.List;
import java.util.Locale;

import static org.ost.advertisement.constants.I18nKey.LOCALE_ENGLISH;
import static org.ost.advertisement.constants.I18nKey.LOCALE_UKRAINIAN;

@SpringComponent
@UIScope
public class LocaleSelectorComponent extends HorizontalLayout {

    private final transient UserService userService;
    private final transient I18nService i18n;

    public LocaleSelectorComponent(UserService userService, I18nService i18n) {
        this.userService = userService;
        this.i18n = i18n;

        addClassName("locale-selector");

        ComboBox<LocaleWrapper> localeSelect = initLocaleSelect();
        add(localeSelect);
    }

    private ComboBox<LocaleWrapper> initLocaleSelect() {
        ComboBox<LocaleWrapper> localeSelect = new ComboBox<>();
        localeSelect.addClassName("locale-combobox");

        List<LocaleWrapper> locales = List.of(
                new LocaleWrapper(i18n.get(LOCALE_ENGLISH), Locale.of("en")),
                new LocaleWrapper(i18n.get(LOCALE_UKRAINIAN), Locale.of("uk"))
        );

        localeSelect.setItems(locales);
        localeSelect.setItemLabelGenerator(LocaleWrapper::label);

        LocaleWrapper selected = locales.stream()
                .filter(wrapper -> wrapper.locale().getLanguage().equals(SessionUtil.getCurrentLocale().getLanguage()))
                .findFirst()
                .orElse(locales.getFirst());

        localeSelect.setValue(selected);

        localeSelect.addValueChangeListener(event -> {
            LocaleWrapper newValue = event.getValue();
            if (newValue == null) return;

            Locale newLocale = newValue.locale();
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

        return localeSelect;
    }

    private record LocaleWrapper(String label, Locale locale) {
        @NotNull
        @Override
        public String toString() {
            return label;
        }
    }
}
