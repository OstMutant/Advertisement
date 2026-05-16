package org.ost.advertisement.audit.model;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.audit.api.AuditableSnapshot;
import org.ost.advertisement.core.model.ChangeEntry;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuditDiffEngine {

    private final AuditFieldCache fieldCache;

    public List<ChangeEntry> diff(AuditableSnapshot before, AuditableSnapshot after) {
        List<Field> fields = fieldCache.getAuditedFields(after.getClass());
        List<ChangeEntry> changes = new ArrayList<>();
        for (Field f : fields) {
            try {
                Object prev = before != null ? f.get(before) : null;
                Object next = f.get(after);
                if (Objects.equals(prev, next)) continue;
                changes.add(toEntry(f, prev, next));
            } catch (IllegalAccessException _) {
                // setAccessible(true) called by AuditFieldCache — should not happen
            }
        }
        return changes;
    }

    public List<ChangeEntry> diffFromNull(AuditableSnapshot after) {
        return diff(null, after);
    }

    private ChangeEntry toEntry(Field field, Object from, Object to) {
        Class<?> type = field.getType();
        if (type == int.class || type == Integer.class) {
            int fromInt = from == null ? 0 : (int) from;
            int toInt   = to   == null ? 0 : (int) to;
            return new ChangeEntry.SettingChange(field.getName(), fromInt, toInt);
        }
        String fromStr = from == null ? null : String.valueOf(from);
        String toStr   = to   == null ? null : String.valueOf(to);
        return new ChangeEntry.FieldChange(field.getName(), truncate(fromStr), truncate(toStr));
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 120 ? s.substring(0, 120) + "\u2026" : s;
    }
}
