package org.ost.marketplace.ui.views.services.pagination;

import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.entities.UserSettings;
import org.ost.marketplace.events.SettingsChangedEvent;
import org.ost.marketplace.ui.views.components.PaginationBar;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Scope;
import org.springframework.context.event.ApplicationEventMulticaster;

import java.util.function.ToIntFunction;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class SettingsPaginationBinding {

    private final SettingsPaginationService   support;
    private final ApplicationEventMulticaster eventMulticaster;

    private ApplicationListener<SettingsChangedEvent> listener;

    public void register(PaginationBar bar, ToIntFunction<UserSettings> pageSizeExtractor, Runnable refresh) {
        support.applyOnInit(bar, pageSizeExtractor);
        listener = event -> support.handleSettingsChanged(event, bar, pageSizeExtractor, refresh);
        eventMulticaster.addApplicationListener(listener);
    }

    public void unregister() {
        if (listener != null) eventMulticaster.removeApplicationListener(listener);
    }
}
