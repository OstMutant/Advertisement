package org.ost.advertisement.audit.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.events.model.ChangeEntry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AuditSnapshotMapper {

    static final TypeReference<List<ChangeEntry>> CHANGES_TYPE = new TypeReference<>() {};

    @Qualifier("userSettingsObjectMapper") private final ObjectMapper objectMapper;

    public String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    public <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            return null;
        }
    }

    public List<ChangeEntry> fromJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, CHANGES_TYPE);
        } catch (Exception e) {
            return List.of();
        }
    }

    public String toChangesJson(List<ChangeEntry> changes) {
        if (changes == null) return null;
        try {
            return objectMapper.writerFor(CHANGES_TYPE).writeValueAsString(changes);
        } catch (Exception e) {
            return null;
        }
    }
}
