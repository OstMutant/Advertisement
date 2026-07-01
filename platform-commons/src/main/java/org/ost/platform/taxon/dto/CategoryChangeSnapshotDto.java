package org.ost.platform.taxon.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;

import java.util.List;
import java.util.Optional;

@JsonTypeName("category_change")
@FieldNameConstants
public record CategoryChangeSnapshotDto(
        EntityType entityType,
        String categoryName,
        boolean assigned,
        String title,
        String description
) implements AuditableSnapshot {

    @Override
    public EntityType entityType() { return entityType; }

    @Override
    public Optional<String> displayName() { return Optional.ofNullable(categoryName); }

    @Override
    public List<ChangeEntry> diff(AuditableSnapshot previous) {
        CategoryChangeSnapshotDto prev = previous instanceof CategoryChangeSnapshotDto p ? p : null;
        boolean wasAssigned = prev != null && prev.assigned();
        if (assigned == wasAssigned) return List.of();
        String from = wasAssigned ? categoryName : "";
        String to   = assigned   ? categoryName : "";
        return List.of(new ChangeEntry.FieldChange(Fields.categoryName, from, to));
    }

    @Override
    public List<ChangeEntry.FieldChange> allFields() {
        return List.of(
                new ChangeEntry.FieldChange(Fields.title,        null, title),
                new ChangeEntry.FieldChange(Fields.description,  null, description),
                new ChangeEntry.FieldChange(Fields.categoryName, null, assigned ? categoryName : ""));
    }

    @Override
    public boolean isRestorable() { return false; }
}
