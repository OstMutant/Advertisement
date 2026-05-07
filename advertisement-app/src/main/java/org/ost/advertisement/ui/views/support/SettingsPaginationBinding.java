package org.ost.advertisement.ui.views.support;

import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.UserSettings;
import org.ost.advertisement.events.SettingsChangedEvent;
import org.ost.advertisement.ui.views.components.PaginationBar;
import org.ost.advertisement.ui.views.services.SettingsPaginationService;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Scope;
import org.springframework.context.event.ApplicationEventMulticaster;

import java.util.function.Function;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class SettingsPaginationBinding {

    private final SettingsPaginationService   support;
    private final ApplicationEventMulticaster eventMulticaster;

    private ApplicationListener<SettingsChangedEvent> listener;

    public void register(PaginationBar bar, Function<UserSettings, Integer> pageSizeExtractor, Runnable refresh) {
        support.applyOnInit(bar, pageSizeExtractor);
        listener = event -> support.handleSettingsChanged(event, bar, pageSizeExtractor, refresh);
        eventMulticaster.addApplicationListener(listener);
    }

    public void unregister() {
        if (listener != null) eventMulticaster.removeApplicationListener(listener);
    }
}
