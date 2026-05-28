package org.ost.platform.audit.api;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.ost.platform.core.model.EntityType;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@type")
public interface AuditableSnapshot {
    EntityType entityType();
}
