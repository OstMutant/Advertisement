package org.ost.platform.advertisement.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.ost.platform.audit.api.AuditableSnapshot.diffField;
import static org.ost.platform.audit.api.AuditableSnapshot.field;
import static org.ost.platform.core.model.ChangeEntry.FieldChange;

@JsonTypeName("advertisement")
@FieldNameConstants
public record AdvertisementSnapshotDto(
        String title,
        String description,
        List<Long> categoryIds,
        Long attachmentSnapshotId
) implements AuditableSnapshot {

    public AdvertisementSnapshotDto {
        categoryIds = categoryIds != null ? List.copyOf(categoryIds.stream().sorted().toList()) : List.of();
    }

    @Override
    public EntityType entityType() { return EntityType.ADVERTISEMENT; }

    @Override
    public Optional<String> displayName() { return Optional.ofNullable(title()); }

    @Override
    public List<ChangeEntry> diff(AuditableSnapshot previous) {
        AdvertisementSnapshotDto prev = previous instanceof AdvertisementSnapshotDto p ? p : null;
        List<ChangeEntry> changes = new ArrayList<>();
        diffField(changes, Fields.title,       field(prev, AdvertisementSnapshotDto::title),       title());
        diffField(changes, Fields.description, field(prev, AdvertisementSnapshotDto::description), description());
        List<Long> prevIds = prev != null ? prev.categoryIds() : List.of();
        if (!Objects.equals(prevIds, categoryIds()))
            changes.add(new FieldChange(Fields.categoryIds, idsToString(prevIds), idsToString(categoryIds())));
        return changes;
    }

    @Override
    public List<ChangeEntry.FieldChange> allFields() {
        return List.of(
                new FieldChange(Fields.title,       null, title()),
                new FieldChange(Fields.description, null, description()),
                new FieldChange(Fields.categoryIds, null, idsToString(categoryIds())));
    }

    private static String idsToString(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return "";
        return ids.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }
}
