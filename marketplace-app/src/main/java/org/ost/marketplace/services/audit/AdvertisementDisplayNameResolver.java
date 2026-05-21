package org.ost.marketplace.services.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ost.platform.audit.dto.SnapshotPayloadDto;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.spi.EntityDisplayNameResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class AdvertisementDisplayNameResolver implements EntityDisplayNameResolver {

    private final ObjectMapper objectMapper;

    public AdvertisementDisplayNameResolver(@Qualifier("userSettingsObjectMapper") ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(EntityType entityType) {
        return entityType == EntityType.ADVERTISEMENT
                || entityType == EntityType.USER
                || entityType == EntityType.USER_SETTINGS;
    }

    @Override
    public String resolveDisplayName(EntityType entityType, SnapshotPayloadDto snapshot) {
        if (snapshot == null || snapshot.isEmpty()) return "";
        try {
            return switch (entityType) {
                case ADVERTISEMENT -> objectMapper.readValue(snapshot.json(), AdvertisementSnapshot.class).title();
                case USER          -> objectMapper.readValue(snapshot.json(), UserSnapshot.class).name();
                case USER_SETTINGS -> "Settings";
            };
        } catch (Exception _) {
            return "";
        }
    }
}
