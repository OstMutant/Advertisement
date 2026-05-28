package org.ost.audit.services;

import org.ost.platform.audit.api.AuditedField;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuditFieldCacheService {

    private final ConcurrentHashMap<Class<?>, List<Field>> cache = new ConcurrentHashMap<>();

    @SuppressWarnings("java:S3011")
    public List<Field> getAuditedFields(Class<?> type) {
        return cache.computeIfAbsent(type, t -> {
            List<Field> fields = Arrays.stream(t.getDeclaredFields())
                    .filter(f -> f.isAnnotationPresent(AuditedField.class))
                    .toList();
            fields.forEach(f -> f.setAccessible(true));
            return fields;
        });
    }

}
