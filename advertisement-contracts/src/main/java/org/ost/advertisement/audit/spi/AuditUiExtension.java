package org.ost.advertisement.audit.spi;

import com.vaadin.flow.component.Component;
import lombok.Value;
import org.ost.advertisement.audit.dto.AdvertisementHistoryDto;
import org.ost.advertisement.core.config.UserSettings;
import org.ost.advertisement.core.model.Role;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface AuditUiExtension {

    @Value
    @lombok.Builder
    class UserActivityParams {
        Long                   userId;
        String                 userName;
        Role                   userRole;
        UserSettings           currentSettings;
        Consumer<Long>         onRestoreUser;
        Consumer<UserSettings> onRestoreSettings;
    }

    @Value
    @lombok.Builder
    class AdvertisementHistoryParams {
        Long                                      adId;
        Long                                      userId;
        boolean                                   isPrivileged;
        String                                    currentTitle;
        String                                    currentDesc;
        boolean                                   canOperate;
        BiConsumer<AdvertisementHistoryDto, Long> onRestoreRequested;
        String                                    labelEmpty;
        String                                    labelCurrentState;
        String                                    labelRestore;
    }

    Component buildUserActivityPanel(UserActivityParams params);

    Component buildAdvertisementHistoryPanel(AdvertisementHistoryParams params);
}
