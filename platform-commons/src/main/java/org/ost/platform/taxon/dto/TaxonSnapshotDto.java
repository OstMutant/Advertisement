package org.ost.platform.taxon.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
        String prevNameEn = field(prev, TaxonSnapshotDto::nameEn);
        String prevDescEn = field(prev, TaxonSnapshotDto::descriptionEn);
        String prevNameUk = field(prev, TaxonSnapshotDto::nameUk);
        String prevDescUk = field(prev, TaxonSnapshotDto::descriptionUk);
        if (!Objects.equals(prevNameEn, nameEn()))
            changes.add(new FieldChange(Fields.nameEn, prevNameEn, nameEn()));
        if (!Objects.equals(prevDescEn, descriptionEn()))
            changes.add(new FieldChange(Fields.descriptionEn, prevDescEn, descriptionEn()));
        if (!Objects.equals(prevNameUk, nameUk()))
            changes.add(new FieldChange(Fields.nameUk, prevNameUk, nameUk()));
        if (!Objects.equals(prevDescUk, descriptionUk()))
            changes.add(new FieldChange(Fields.descriptionUk, prevDescUk, descriptionUk()));
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
