package org.ost.audit.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditJsonSerializationService {

    static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Qualifier("auditObjectMapper") private final ObjectMapper objectMapper;

    public String toSnapshotJson(AuditableSnapshot snapshot) {
        if (snapshot == null) return null;
        try {
            return objectMapper.writerFor(AuditableSnapshot.class).writeValueAsString(snapshot);
        } catch (Exception _) {
            return null;
        }
    }

    public AuditableSnapshot fromSnapshot(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, AuditableSnapshot.class);
        } catch (Exception _) {
            return null;
        }
    }

    public Map<String, Object> toMap(AuditableSnapshot snapshot) {
        if (snapshot == null) return null;
        try {
            String json = toSnapshotJson(snapshot);
            Map<String, Object> map = objectMapper.readValue(json, MAP_TYPE);
            map.keySet().removeIf(k -> k.startsWith("@"));
            return map;
        } catch (Exception _) {
            return null;
        }
    }
}
