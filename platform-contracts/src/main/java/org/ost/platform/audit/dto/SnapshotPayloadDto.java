package org.ost.platform.audit.dto;

public record SnapshotPayloadDto(String json) {
    public boolean isEmpty() { return json == null || json.isBlank(); }
}
