package org.ost.platform.core.config;

/**
 * Marker for opaque preference snapshots that the audit subsystem may capture
 * for any subject (user, organization, project, ...). The audit-starter treats
 * the implementation as a JSON-serializable bag — it never reads individual fields.
 * Each domain module defines its own concrete preferences type implementing this.
 */
public interface SubjectPreferences {}
