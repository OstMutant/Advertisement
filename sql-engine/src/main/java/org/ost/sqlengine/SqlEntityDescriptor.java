package org.ost.sqlengine;

/**
 * Marker for classes that describe an entity table on both sides — reads and writes.
 *
 * <p>Conventional layout:
 * <ul>
 *   <li>Public column-name constants and {@code SqlSelectField} fields at the top
 *       (shared between {@code Read} and {@code Write}).</li>
 *   <li>{@code public static final class Read} — {@code PROJECTION} for full-row
 *       mapping, {@code SELECT_*} SQL constants, and param-factory methods.</li>
 *   <li>{@code public static final class Write} — {@code SqlCommand} constants
 *       and matching param-factory methods.</li>
 * </ul>
 *
 * <p>Implementations should be {@code final} with a private constructor — descriptors
 * are pure namespaces, not entities.
 */
public interface SqlEntityDescriptor {}
