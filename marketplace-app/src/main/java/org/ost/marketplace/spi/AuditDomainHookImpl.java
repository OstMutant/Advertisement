package org.ost.marketplace.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.marketplace.dto.audit.AdvertisementSnapshotDto;
import org.ost.marketplace.dto.audit.SettingsSnapshotDto;
import org.ost.marketplace.dto.audit.UserSnapshotDto;
import org.ost.marketplace.services.AdvertisementService;
import org.ost.marketplace.services.user.UserService;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditSnapshotContentDto;
import org.ost.platform.audit.spi.AuditDomainHook;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditDomainHookImpl implements AuditDomainHook {

    private final AdvertisementService advertisementService;
    private final UserService          userService;

    @Override
    public Map<Long, String> resolveNames(@NonNull Set<Long> actorIds) {
        return userService.findActorNames(actorIds);
    }

    @Override
    public Set<Long> findExisting(@NonNull EntityType entityType, @NonNull Set<Long> entityIds) {
        Long[] ids = entityIds.toArray(new Long[0]);
        return Set.copyOf(switch (entityType) {
            case ADVERTISEMENT       -> advertisementService.findExistingIds(ids);
            case USER, USER_SETTINGS -> userService.findExistingIds(ids);
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends AuditableSnapshot> Optional<AuditSnapshotContentDto<T>> castIfKnown(AuditSnapshotContentDto<? extends AuditableSnapshot> content) {
        AuditableSnapshot data = content.snapshotData();
        return switch (data) {
            case AdvertisementSnapshotDto _, UserSnapshotDto _, SettingsSnapshotDto _ -> Optional.of((AuditSnapshotContentDto<T>) content);
            default -> {
                log.error("Snapshot type mismatch for entityType={}", data.entityType());
                yield Optional.empty();
            }
        };
    }

    @Override
    public String resolveDisplayName(@NonNull EntityType entityType, @NonNull AuditableSnapshot snapshot) {
        return snapshot.displayName().orElse("");
    }
}
