package org.ost.advertisement.audit;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.events.model.ChangeEntry;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuditDiffEngine {

    private final AuditFieldCache fieldCache;

    public Map<String, Object> toSnapshotMap(Object snapshot) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Field f : fieldCache.getAuditedFields(snapshot.getClass())) {
            try {
                map.put(f.getName(), f.get(snapshot));
            } catch (IllegalAccessException e) {
                map.put(f.getName(), null);
            }
        }
        return map;
    }

    public List<ChangeEntry> diff(Object before, Object after) {
        List<Field> fields = fieldCache.getAuditedFields(after.getClass());
        List<ChangeEntry> changes = new ArrayList<>();
        for (Field f : fields) {
            try {
                Object prev = before != null ? f.get(before) : null;
                Object next = f.get(after);
                if (Objects.equals(prev, next)) continue;
                changes.add(toEntry(f, prev, next));
            } catch (IllegalAccessException ignored) {
            }
        }
        return changes;
    }

    public List<ChangeEntry> diffFromNull(Object after) {
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
        return s.length() > 40 ? s.substring(0, 40) + "\u2026" : s;
    }
}
