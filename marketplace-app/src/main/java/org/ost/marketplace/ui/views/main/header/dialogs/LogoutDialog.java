package org.ost.marketplace.ui.views.main.header.dialogs;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.auth.AuthService;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.views.services.VaadinLocaleProvider;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
public class LogoutDialog extends ConfirmDialog {

    private final transient AuthService authService;
    private final transient I18nService i18n;
    private final transient VaadinLocaleProvider vaadinLocaleProvider;

    @PostConstruct
    private void initDialog() {
        setText(i18n.get(LOGOUT_CONFIRM_TEXT));
        setConfirmButton(i18n.get(LOGOUT_CONFIRM_YES), _ -> handleLogout());
        setCancelButton(i18n.get(LOGOUT_CONFIRM_CANCEL), _ -> close());
    }

    private void handleLogout() {
        UI ui = UI.getCurrent();
        authService.logout();
        close();
        vaadinLocaleProvider.refreshCurrentLocale(ui);
        ui.getPage().reload();
    }
}
