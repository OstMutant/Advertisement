package org.ost.user.events;

import lombok.Getter;
import org.ost.platform.user.dto.UserSettings;
import org.springframework.context.ApplicationEvent;

@Getter
public class UserSettingsChangedEvent extends ApplicationEvent {

    private final Long         userId;
    private final UserSettings settings;

    public UserSettingsChangedEvent(Object source, Long userId, UserSettings settings) {
        super(source);
        this.userId   = userId;
        this.settings = settings;
    }
}
