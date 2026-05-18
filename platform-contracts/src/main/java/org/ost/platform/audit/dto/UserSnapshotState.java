package org.ost.platform.audit.dto;

import org.ost.platform.core.model.Role;

public record UserSnapshotState(Long userId, String name, Role role) {}
