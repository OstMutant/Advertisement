package org.ost.advertisement.audit.dto;

import org.ost.advertisement.core.model.Role;

public record UserSnapshotState(Long userId, String name, Role role) {}
