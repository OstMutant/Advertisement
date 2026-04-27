package org.ost.advertisement.ui.views.support;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.UserSettings;
import org.ost.advertisement.events.SettingsChangedEvent;
import org.ost.advertisement.services.UserSettingsService;
import org.ost.advertisement.services.auth.AuthContextService;
import org.ost.advertisement.ui.views.components.PaginationBarModern;

import java.util.function.Function;

@SpringComponent
@RequiredArgsConstructor
public class SettingsPaginationSupport {

    private final AuthContextService  authContextService;
    private final UserSettingsService settingsService;

    public void applyOnInit(PaginationBarModern paginationBar, Function<UserSettings, Integer> pageSizeExtractor) {
        authContextService.getCurrentUser().ifPresent(user ->
                paginationBar.setPageSize(pageSizeExtractor.apply(settingsService.load(user.getId())))
        );
    }

    public void handleSettingsChanged(
            SettingsChangedEvent event,
            PaginationBarModern paginationBar,
            Function<UserSettings, Integer> pageSizeExtractor,
            Runnable refresh) {

        authContextService.getCurrentUser()
                .filter(u -> u.getId().equals(event.getUserId()))
                .ifPresent(_ -> {
                    UI ui = UI.getCurrent();
                    if (ui != null) {
                        ui.access(() -> {
                            paginationBar.setPageSize(pageSizeExtractor.apply(event.getSettings()));
                            refresh.run();
                        });
                    }
                });
    }
}
