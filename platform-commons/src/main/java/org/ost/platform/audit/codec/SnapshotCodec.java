package org.ost.platform.audit.codec;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.dto.SnapshotPayloadDto;

import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
public class SnapshotCodec {

    private final ObjectMapper objectMapper;

    public <T> Optional<T> decode(SnapshotPayloadDto payload, Class<T> clazz) {
        if (payload == null || payload.isEmpty()) return Optional.empty();
        try {
            return Optional.ofNullable(objectMapper.readValue(payload.json(), clazz));
        } catch (Exception _) {
            return Optional.empty();
        }
    }

    public Optional<Map<String, Object>> decodeToMap(SnapshotPayloadDto payload) {
        if (payload == null || payload.isEmpty()) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(payload.json(), new TypeReference<>() {}));
        } catch (Exception _) {
            return Optional.empty();
        }
    }

    public boolean jsonEquals(SnapshotPayloadDto a, SnapshotPayloadDto b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        try {
            return objectMapper.readTree(a.json()).equals(objectMapper.readTree(b.json()));
        } catch (Exception _) {
            return false;
        }
    }
}
