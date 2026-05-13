package org.ost.advertisement.audit.model;

import org.ost.advertisement.audit.AuditedField;
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

}
