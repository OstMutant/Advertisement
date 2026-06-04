package org.ost.platform.audit.dto;

import org.ost.platform.audit.api.AuditableSnapshot;

public record AuditSnapshotContentDto<T extends AuditableSnapshot>(T snapshotData, int version) {}
