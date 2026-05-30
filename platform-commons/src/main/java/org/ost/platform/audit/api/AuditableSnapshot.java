package org.ost.platform.audit.api;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;

import java.util.List;
import java.util.function.Function;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
public interface AuditableSnapshot {
    EntityType entityType();
    List<ChangeEntry> diff(AuditableSnapshot previous);
    List<ChangeEntry> allFields();

    static <S extends AuditableSnapshot, T> T field(S snapshot, Function<S, T> getter) {
        return snapshot != null ? getter.apply(snapshot) : null;
    }

    static String trunc(String s) {
        if (s == null) return null;
        return s.length() > 120 ? s.substring(0, 120) + "\u2026" : s;
    }
}
