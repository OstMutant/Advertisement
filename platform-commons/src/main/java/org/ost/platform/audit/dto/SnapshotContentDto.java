package org.ost.platform.audit.dto;

import org.ost.platform.audit.api.AuditableSnapshot;

public record SnapshotContentDto(AuditableSnapshot snapshotData, int version) {}
