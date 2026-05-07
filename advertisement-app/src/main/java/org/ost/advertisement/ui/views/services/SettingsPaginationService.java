package org.ost.advertisement.ui.views.services;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.UserSettings;
import org.ost.advertisement.events.SettingsChangedEvent;
import org.ost.advertisement.services.user.UserSettingsService;
import org.ost.advertisement.services.auth.AuthContextService;
import org.ost.advertisement.ui.views.components.PaginationBar;

import java.util.function.Function;

@SpringComponent
@RequiredArgsConstructor
public class SettingsPaginationService {

    private final AuthContextService  authContextService;
    private final UserSettingsService settingsService;

    public void applyOnInit(PaginationBar paginationBar, Function<UserSettings, Integer> pageSizeExtractor) {
        authContextService.getCurrentUser().ifPresent(user ->
                paginationBar.setPageSize(pageSizeExtractor.apply(settingsService.load(user.getId())))
        );
    }

    public void handleSettingsChanged(
            SettingsChangedEvent event,
            PaginationBar paginationBar,
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
