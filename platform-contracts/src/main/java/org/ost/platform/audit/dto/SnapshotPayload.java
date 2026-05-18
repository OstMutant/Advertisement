package org.ost.platform.audit.dto;

public record SnapshotPayload(String json) {
    public boolean isEmpty() { return json == null || json.isBlank(); }
}
