package org.ost.platform.audit.spi;

import com.vaadin.flow.component.Component;
import lombok.Value;
import org.ost.platform.audit.dto.EntityHistoryDto;
import org.ost.platform.core.model.EntityType;

import java.util.List;
import java.util.function.ObjLongConsumer;

public interface AuditUiExtension {

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

    @Value
    @lombok.Builder
    class ProfileActivityParams {
        EntityType                 subjectType;
        Long                       subjectId;
        Long                       viewerActorId;
        String                     emptyLabel;
        @lombok.Builder.Default
        List<ActivityRowBinding>   bindings = List.of();
    }

    Component buildEntityHistoryPanel(EntityHistoryParams params);

    Component buildProfileActivityPanel(ProfileActivityParams params);
}
