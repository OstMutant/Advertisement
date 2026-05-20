package org.ost.marketplace.ui.views.services.pagination;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.entities.UserSettings;
import org.ost.marketplace.events.SettingsChangedEvent;
import org.ost.marketplace.services.user.UserSettingsService;
import org.ost.marketplace.services.auth.AuthContextService;
import org.ost.marketplace.ui.views.components.PaginationBar;

import java.util.function.ToIntFunction;

@SpringComponent
@RequiredArgsConstructor
public class SettingsPaginationService {

    private final AuthContextService  authContextService;
    private final UserSettingsService settingsService;

    public void applyOnInit(PaginationBar paginationBar, ToIntFunction<UserSettings> pageSizeExtractor) {
        authContextService.getCurrentUser().ifPresent(user ->
                paginationBar.setPageSize(pageSizeExtractor.applyAsInt(settingsService.load(user.getId())))
        );
    }

    public void handleSettingsChanged(
            SettingsChangedEvent event,
            PaginationBar paginationBar,
            ToIntFunction<UserSettings> pageSizeExtractor,
            Runnable refresh) {

        authContextService.getCurrentUser()
                .filter(u -> u.getId().equals(event.getUserId()))
                .ifPresent(_ -> {
                    UI ui = UI.getCurrent();
                    if (ui != null) {
                        ui.access(() -> {
                            paginationBar.setPageSize(pageSizeExtractor.applyAsInt(event.getSettings()));
                            refresh.run();
                        });
                    }
                });
    }
}
