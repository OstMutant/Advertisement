package org.ost.marketplace.ui.views.main.header;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.ost.platform.core.i18n.I18nService;
import org.ost.platform.core.i18n.LocaleProvider;
import org.ost.user.services.UserService;
import org.ost.marketplace.services.auth.AuthContextService;

import java.util.List;
import java.util.Locale;

import static org.ost.marketplace.common.I18nKey.LOCALE_ENGLISH;
import static org.ost.marketplace.common.I18nKey.LOCALE_UKRAINIAN;

@SpringComponent
@UIScope
@RequiredArgsConstructor
public class LocaleSelectorComponent extends HorizontalLayout {

    private final transient UserService userService;
    private final transient I18nService i18n;
    private final transient LocaleProvider localeProvider;
    private final transient AuthContextService authContextService;

    @PostConstruct
    protected void init() {
        addClassName("locale-selector");
        add(initLocaleSelect());
    }

    private ComboBox<LocaleWrapper> initLocaleSelect() {
        ComboBox<LocaleWrapper> localeSelect = new ComboBox<>();
        localeSelect.addClassName("locale-combobox");

        List<LocaleWrapper> locales = getAvailableLocales();
        localeSelect.setItems(locales);
        localeSelect.setItemLabelGenerator(LocaleWrapper::label);

        setCurrentLocale(localeSelect, locales);

        localeSelect.addValueChangeListener(event -> {
            LocaleWrapper newValue = event.getValue();
            if (newValue != null) {
                handleLocaleChange(newValue.locale());
            }
        });

        return localeSelect;
    }

    private List<LocaleWrapper> getAvailableLocales() {
        return List.of(
                new LocaleWrapper(i18n.get(LOCALE_ENGLISH), Locale.of("en")),
                new LocaleWrapper(i18n.get(LOCALE_UKRAINIAN), Locale.of("uk"))
        );
    }

    private void setCurrentLocale(ComboBox<LocaleWrapper> localeSelect, List<LocaleWrapper> locales) {
        Locale current = localeProvider.getCurrentLocale();
        locales.stream()
                .filter(wrapper -> wrapper.locale().getLanguage().equals(current.getLanguage()))
                .findFirst()
                .ifPresentOrElse(localeSelect::setValue, () -> localeSelect.setValue(locales.getFirst()));
    }

    private void handleLocaleChange(Locale newLocale) {
        authContextService.getCurrentUser().ifPresentOrElse(currentUser -> {
            userService.updateLocale(currentUser.getId(), newLocale.toLanguageTag());
            authContextService.updateCurrentUser(currentUser.withLocale(newLocale.toLanguageTag()));
        }, () -> {
            UI ui = UI.getCurrent();
            if (ui != null && ui.getSession() != null) {
                ui.getSession().setLocale(newLocale);
            }
        });
        localeProvider.refreshCurrentLocale();
        UI.getCurrent().getPage().reload();
    }

    private record LocaleWrapper(String label, Locale locale) {
        @NotNull
        @Override
        public String toString() {
            return label;
        }
    }
}
