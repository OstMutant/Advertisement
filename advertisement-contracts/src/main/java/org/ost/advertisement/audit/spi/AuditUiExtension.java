package org.ost.advertisement.audit.spi;

import com.vaadin.flow.component.Component;
import lombok.Value;
import org.ost.advertisement.audit.dto.EntityHistoryDto;
import org.ost.advertisement.core.config.UserSettings;
import org.ost.advertisement.core.model.EntityType;
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
    class EntityHistoryParams {
        EntityType                             entityType;
        Long                                   entityId;
        Long                                   userId;
        boolean                                isPrivileged;
        boolean                                canOperate;
        BiConsumer<EntityHistoryDto, Long>     onRestoreRequested;
        String                                 labelEmpty;
        String                                 labelCurrentState;
        String                                 labelRestore;
    }

    Component buildUserActivityPanel(UserActivityParams params);

    Component buildEntityHistoryPanel(EntityHistoryParams params);
}
