package org.ost.platform.audit.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
public interface AuditableSnapshot {
    EntityType entityType();
    Optional<String> displayName();
    List<ChangeEntry> diff(AuditableSnapshot previous);
    List<ChangeEntry.FieldChange> allFields();

    default List<ChangeEntry> expandWithChanges(List<ChangeEntry> changes) {
        Map<String, ChangeEntry.FieldChange> index = new HashMap<>();
        changes.stream()
                .filter(ChangeEntry.FieldChange.class::isInstance)
                .map(ChangeEntry.FieldChange.class::cast)
                .forEach(fc -> index.put(fc.field(), fc));
        return allFields().stream()
                .<ChangeEntry>map(f -> index.getOrDefault(f.field(), f))
                .toList();
    }

    @JsonIgnore
    default boolean isVisible() { return true; }

    static <S extends AuditableSnapshot, T> T field(S snapshot, Function<S, T> getter) {
        return snapshot != null ? getter.apply(snapshot) : null;
    }
}
