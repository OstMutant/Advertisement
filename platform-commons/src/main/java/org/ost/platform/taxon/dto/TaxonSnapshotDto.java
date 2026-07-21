package org.ost.platform.taxon.dto;

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

@JsonTypeName("taxon_state")
@FieldNameConstants
public record TaxonSnapshotDto(
        String nameEn,
        String descriptionEn,
        String nameUk,
        String descriptionUk
) implements AuditableSnapshot {

    @Override
    public EntityType entityType() { return EntityType.TAXON; }

    @Override
    public Optional<String> displayName() { return Optional.ofNullable(nameEn()); }

    @Override
    public List<ChangeEntry> diff(AuditableSnapshot previous) {
        TaxonSnapshotDto prev = previous instanceof TaxonSnapshotDto p ? p : null;
        List<ChangeEntry> changes = new ArrayList<>();
        diffField(changes, Fields.nameEn,        field(prev, TaxonSnapshotDto::nameEn),        nameEn());
        diffField(changes, Fields.descriptionEn, field(prev, TaxonSnapshotDto::descriptionEn), descriptionEn());
        diffField(changes, Fields.nameUk,        field(prev, TaxonSnapshotDto::nameUk),        nameUk());
        diffField(changes, Fields.descriptionUk, field(prev, TaxonSnapshotDto::descriptionUk), descriptionUk());
        return changes;
    }

    @Override
    public List<ChangeEntry.FieldChange> allFields() {
        return List.of(
                new FieldChange(Fields.nameEn,          null, nameEn()),
                new FieldChange(Fields.descriptionEn,   null, descriptionEn()),
                new FieldChange(Fields.nameUk,          null, nameUk()),
                new FieldChange(Fields.descriptionUk,   null, descriptionUk()));
    }
}
