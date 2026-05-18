package org.ost.platform.audit.spi;

import com.vaadin.flow.component.Component;
import lombok.Value;
import org.ost.platform.audit.dto.EntityHistoryDto;
import org.ost.platform.core.config.UserSettings;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.model.Role;

import java.util.function.ObjLongConsumer;
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
        ObjLongConsumer<EntityHistoryDto>      onRestoreRequested;
        String                                 labelEmpty;
        String                                 labelCurrentState;
        String                                 labelRestore;
    }

    Component buildUserActivityPanel(UserActivityParams params);

    Component buildEntityHistoryPanel(EntityHistoryParams params);
}
