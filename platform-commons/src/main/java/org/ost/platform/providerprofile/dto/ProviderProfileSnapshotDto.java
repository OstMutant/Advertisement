package org.ost.platform.providerprofile.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.providerprofile.model.ProviderKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.ost.platform.audit.api.AuditableSnapshot.diffField;
import static org.ost.platform.audit.api.AuditableSnapshot.field;
import static org.ost.platform.core.model.ChangeEntry.FieldChange;

@JsonTypeName("provider_profile")
@FieldNameConstants
public record ProviderProfileSnapshotDto(
        ProviderKind kind,
        String about,
        List<Long> categoryIds,
        Long cityTaxonId,
        String phone,
        String telegram,
        String viber,
        int schemaVersion
) implements AuditableSnapshot {

    public static final int SCHEMA_VERSION = 1;

    public ProviderProfileSnapshotDto {
        categoryIds = categoryIds != null ? List.copyOf(categoryIds.stream().sorted().toList()) : List.of();
    }

    // Pre-existing shape, kept for callers that don't set contact fields.
    public ProviderProfileSnapshotDto(ProviderKind kind, String about, List<Long> categoryIds, Long cityTaxonId) {
        this(kind, about, categoryIds, cityTaxonId, null, null, null, SCHEMA_VERSION);
    }

    public ProviderProfileSnapshotDto(ProviderKind kind, String about, List<Long> categoryIds, Long cityTaxonId,
                                       String phone, String telegram, String viber) {
        this(kind, about, categoryIds, cityTaxonId, phone, telegram, viber, SCHEMA_VERSION);
    }

    @Override
    public EntityType entityType() { return EntityType.PROVIDER_PROFILE; }

    @Override
    public Optional<String> displayName() { return Optional.ofNullable(kind()).map(ProviderKind::name); }

    @Override
    public List<ChangeEntry> diff(AuditableSnapshot previous) {
        ProviderProfileSnapshotDto prev = previous instanceof ProviderProfileSnapshotDto p ? p : null;
        List<ChangeEntry> changes = new ArrayList<>();
        diffField(changes, Fields.kind,  typeToString(field(prev, ProviderProfileSnapshotDto::kind)),  typeToString(kind()));
        diffField(changes, Fields.about, field(prev, ProviderProfileSnapshotDto::about), about());
        List<Long> prevIds = prev != null ? prev.categoryIds() : List.of();
        if (!Objects.equals(prevIds, categoryIds()))
            changes.add(new FieldChange(Fields.categoryIds, idsToString(prevIds), idsToString(categoryIds())));
        Long prevCityId = prev != null ? prev.cityTaxonId() : null;
        if (!Objects.equals(prevCityId, cityTaxonId()))
            changes.add(new FieldChange(Fields.cityTaxonId, idToString(prevCityId), idToString(cityTaxonId())));
        diffField(changes, Fields.phone,    field(prev, ProviderProfileSnapshotDto::phone),    phone());
        diffField(changes, Fields.telegram, field(prev, ProviderProfileSnapshotDto::telegram), telegram());
        diffField(changes, Fields.viber,    field(prev, ProviderProfileSnapshotDto::viber),    viber());
        return changes;
    }

    @Override
    public List<FieldChange> allFields() {
        return List.of(
                new FieldChange(Fields.kind,        null, typeToString(kind())),
                new FieldChange(Fields.about,       null, about()),
                new FieldChange(Fields.categoryIds, null, idsToString(categoryIds())),
                new FieldChange(Fields.cityTaxonId, null, idToString(cityTaxonId())),
                new FieldChange(Fields.phone,       null, phone()),
                new FieldChange(Fields.telegram,    null, telegram()),
                new FieldChange(Fields.viber,       null, viber()));
    }

    private static String idsToString(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return "";
        return ids.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    private static String idToString(Long id) {
        return idsToString(id == null ? null : List.of(id));
    }

    private static String typeToString(ProviderKind kind) {
        return kind == null ? "" : kind.name();
    }
}
