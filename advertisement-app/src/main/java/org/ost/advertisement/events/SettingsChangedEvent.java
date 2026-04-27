package org.ost.advertisement.events;

import lombok.Getter;
import org.ost.advertisement.dto.UserSettings;
import org.springframework.context.ApplicationEvent;

@Getter
public class SettingsChangedEvent extends ApplicationEvent {

    private final Long         userId;
    private final UserSettings settings;

    public SettingsChangedEvent(Object source, Long userId, UserSettings settings) {
        super(source);
        this.userId   = userId;
        this.settings = settings;
    }
}
