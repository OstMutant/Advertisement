package org.ost.advertisement.audit;

import org.ost.advertisement.entities.Role;

public record UserSnapshotState(Long userId, String name, Role role) {}
