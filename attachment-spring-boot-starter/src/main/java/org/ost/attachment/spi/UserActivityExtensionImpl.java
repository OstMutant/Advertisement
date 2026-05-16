package org.ost.attachment.spi;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.events.dto.ActivityItemDto;
import org.ost.advertisement.events.model.EntityType;
import org.ost.advertisement.events.spi.AuditActorNameResolver;
import org.ost.advertisement.events.spi.AuditEntityExistenceChecker;
import org.ost.advertisement.events.spi.AttachmentEntityDisplayNameResolver;
import org.ost.advertisement.events.spi.UserActivityExtension;
import org.ost.advertisement.spi.storage.ConditionalOnStorageEnabled;
import org.ost.attachment.repository.AttachmentActivityProjection;
import org.ost.attachment.service.AttachmentSnapshotService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@ConditionalOnStorageEnabled
@RequiredArgsConstructor
public class UserActivityExtensionImpl implements UserActivityExtension {

    private final JdbcClient                                     jdbcClient;
    private final AttachmentSnapshotService                      attachmentSnapshotService;
    private final ObjectProvider<AuditActorNameResolver>         actorNameResolver;
    private final ObjectProvider<AuditEntityExistenceChecker>    entityExistenceChecker;
    private final ObjectProvider<AttachmentEntityDisplayNameResolver> displayNameResolver;

    @Override
    public List<ActivityItemDto> getMediaActivity(Long userId) {
        AttachmentActivityProjection projection = new AttachmentActivityProjection(attachmentSnapshotService);
        List<ActivityItemDto> raw = projection.queryAll(jdbcClient, new MapSqlParameterSource("userId", userId));

        if (raw.isEmpty()) return raw;

        Set<Long> actorIds   = raw.stream().map(ActivityItemDto::changedByUserId).collect(Collectors.toSet());
        Set<Long> entityIds  = raw.stream().map(ActivityItemDto::entityId).collect(Collectors.toSet());

        AuditActorNameResolver             nameRes     = actorNameResolver.getIfAvailable();
        AuditEntityExistenceChecker        existRes    = entityExistenceChecker.getIfAvailable();
        AttachmentEntityDisplayNameResolver displayRes = displayNameResolver.getIfAvailable();

        Map<Long, String> names        = nameRes     != null ? nameRes.resolveNames(actorIds)                              : Map.of();
        Set<Long>         existing     = existRes    != null ? existRes.findExisting(EntityType.ADVERTISEMENT, entityIds)  : Set.of();
        Map<Long, String> displayNames = displayRes  != null ? displayRes.resolveDisplayNames(entityIds)                   : Map.of();

        return raw.stream()
                .map(item -> new ActivityItemDto(
                        item.snapshotId(),
                        item.entityId(),
                        item.entityType(),
                        displayNames.getOrDefault(item.entityId(), "—"),
                        item.actionType(),
                        item.createdAt(),
                        existing.contains(item.entityId()),
                        item.changes(),
                        item.changedByUserId(),
                        names.getOrDefault(item.changedByUserId(), "—"),
                        displayNames.getOrDefault(item.entityId(), "—"),
                        null,
                        null,
                        null
                ))
                .toList();
    }
}
