package org.ost.advertisement.audit;

import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuditFieldCache {

    private final ConcurrentHashMap<Class<?>, List<Field>> cache = new ConcurrentHashMap<>();

    public List<Field> getAuditedFields(Class<?> type) {
        return cache.computeIfAbsent(type, t ->
                Arrays.stream(t.getDeclaredFields())
                        .filter(f -> f.isAnnotationPresent(AuditedField.class))
                        .peek(f -> f.setAccessible(true))
                        .toList()
        );
    }

    public String resolveI18nKey(Field field) {
        String override = field.getAnnotation(AuditedField.class).i18nKey();
        return override.isBlank() ? "changes.field." + field.getName() : override;
    }
}
