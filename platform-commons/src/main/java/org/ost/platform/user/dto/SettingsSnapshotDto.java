package org.ost.platform.user.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.ost.platform.audit.api.AuditableSnapshot.diffField;
import static org.ost.platform.audit.api.AuditableSnapshot.field;
import static org.ost.platform.core.model.ChangeEntry.FieldChange;

@JsonTypeName("user_settings")
@FieldNameConstants
public record SettingsSnapshotDto(
        int adsPageSize,
        int usersPageSize,
        int timelinePageSize
) implements AuditableSnapshot {

    public static SettingsSnapshotDto from(UserSettingsDto settings) {
        return new SettingsSnapshotDto(settings.getAdsPageSize(), settings.getUsersPageSize(), settings.getTimelinePageSize());
    }

    @Override
    public EntityType entityType() { return EntityType.USER_SETTINGS; }

    @Override
    public Optional<String> displayName() { return Optional.empty(); }

    @Override
    public List<ChangeEntry> diff(AuditableSnapshot previous) {
        SettingsSnapshotDto prev = previous instanceof SettingsSnapshotDto p ? p : null;
        List<ChangeEntry> changes = new ArrayList<>();
        diffField(changes, Fields.adsPageSize,      field(prev, SettingsSnapshotDto::adsPageSize),      adsPageSize());
        diffField(changes, Fields.usersPageSize,    field(prev, SettingsSnapshotDto::usersPageSize),    usersPageSize());
        diffField(changes, Fields.timelinePageSize, field(prev, SettingsSnapshotDto::timelinePageSize), timelinePageSize());
        return changes;
    }

    @Override
    public List<FieldChange> allFields() {
        return List.of(
                new FieldChange(Fields.adsPageSize,      null, String.valueOf(adsPageSize())),
                new FieldChange(Fields.usersPageSize,    null, String.valueOf(usersPageSize())),
                new FieldChange(Fields.timelinePageSize, null, String.valueOf(timelinePageSize())));
    }
}
