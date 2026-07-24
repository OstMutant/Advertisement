package org.ost.marketplace.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.platform.advertisement.spi.AdvertisementPort;
import org.ost.platform.taxon.spi.TaxonPort;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditSnapshotContentDto;
import org.ost.platform.audit.spi.AuditDomainHook;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.user.spi.UserPort;
import org.ost.marketplace.services.user.UserActorNameService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditDomainHookImpl implements AuditDomainHook {

    private final ComponentFactory<AdvertisementPort> advertisementPortFactory;
    private final ComponentFactory<UserPort>          userPortFactory;
    private final ComponentFactory<TaxonPort>         taxonPortFactory;
    private final UserActorNameService                userActorNameService;

    @Override
    public Map<Long, String> resolveNames(@NonNull Set<Long> actorIds) {
        return userActorNameService.resolveNames(actorIds);
    }

    @Override
    public Set<Long> findExisting(@NonNull EntityType entityType, @NonNull Set<Long> entityIds) {
        return switch (entityType) {
            case ADVERTISEMENT       -> advertisementPortFactory.findIfAvailable()
                    .map(p -> p.findExistingIds(entityIds))
                    .orElse(Set.of());
            case USER, USER_SETTINGS -> userPortFactory.findIfAvailable()
                    .map(p -> p.findExistingIds(entityIds))
                    .orElse(Set.of());
            case TAXON               -> taxonPortFactory.findIfAvailable()
                    .map(p -> p.findExistingIds(entityIds))
                    .orElse(Set.of());
        };
    }

    @Override
    public <T extends AuditableSnapshot> Optional<AuditSnapshotContentDto<T>> castIfKnown(
            @NonNull AuditSnapshotContentDto<? extends AuditableSnapshot> content, @NonNull Class<T> targetClass) {
        try {
            return Optional.of(new AuditSnapshotContentDto<>(targetClass.cast(content.snapshotData()), content.version()));
        } catch (ClassCastException _) {
            log.error("Snapshot type mismatch: expected {}, got {}", targetClass.getSimpleName(), content.snapshotData().getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public String resolveDisplayName(@NonNull AuditableSnapshot snapshot) {
        return snapshot.displayName().orElse("");
    }
}
