package org.ost.taxon.repository;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.taxon.entities.TaxonAssignment;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@SuppressWarnings("java:S1192")
public class TaxonAssignmentRepository {

    private static final RowMapper<TaxonAssignment> ROW_MAPPER = (rs, _) -> {
        Timestamp assignedAt = rs.getTimestamp("assigned_at");
        return TaxonAssignment.builder()
                              .entityType(rs.getString("entity_type"))
                              .entityId(rs.getObject("entity_id", Long.class))
                              .taxonId(rs.getObject("taxon_id", Long.class))
                              .assignedAt(assignedAt != null ? assignedAt.toInstant() : null)
                              .assignedBy(rs.getObject("assigned_by", Long.class))
                              .build();
    };

    private final JdbcClient jdbcClient;

    public void assign(@NonNull String entityType, @NonNull Long entityId,
                       @NonNull Long taxonId, Long assignedBy) {
        jdbcClient.sql("""
                        INSERT INTO taxon_assignment (entity_type, entity_id, taxon_id, assigned_at, assigned_by)
                        VALUES (:entityType, :entityId, :taxonId, NOW(), :assignedBy)
                        ON CONFLICT (entity_type, entity_id, taxon_id) DO NOTHING
                        """)
                  .paramSource(new MapSqlParameterSource()
                          .addValue("entityType", entityType)
                          .addValue("entityId",   entityId)
                          .addValue("taxonId",    taxonId)
                          .addValue("assignedBy", assignedBy))
                  .update();
    }

    public void unassign(@NonNull String entityType, @NonNull Long entityId, @NonNull Long taxonId) {
        jdbcClient.sql("""
                        DELETE FROM taxon_assignment
                        WHERE entity_type = :entityType AND entity_id = :entityId AND taxon_id = :taxonId
                        """)
                  .paramSource(new MapSqlParameterSource()
                          .addValue("entityType", entityType)
                          .addValue("entityId",   entityId)
                          .addValue("taxonId",    taxonId))
                  .update();
    }

    public void deleteAllByEntity(@NonNull String entityType, @NonNull Long entityId) {
        jdbcClient.sql("""
                        DELETE FROM taxon_assignment
                        WHERE entity_type = :entityType AND entity_id = :entityId
                        """)
                  .paramSource(new MapSqlParameterSource()
                          .addValue("entityType", entityType)
                          .addValue("entityId",   entityId))
                  .update();
    }

    public List<TaxonAssignment> findAllByEntity(@NonNull String entityType, @NonNull Long entityId) {
        return jdbcClient.sql("""
                        SELECT entity_type, entity_id, taxon_id, assigned_at, assigned_by
                        FROM taxon_assignment
                        WHERE entity_type = :entityType AND entity_id = :entityId
                        """)
                         .paramSource(new MapSqlParameterSource()
                                 .addValue("entityType", entityType)
                                 .addValue("entityId",   entityId))
                         .query(ROW_MAPPER)
                         .list();
    }

    public List<TaxonAssignment> findAllByEntities(@NonNull String entityType, @NonNull Set<Long> entityIds) {
        return jdbcClient.sql("""
                        SELECT entity_type, entity_id, taxon_id, assigned_at, assigned_by
                        FROM taxon_assignment
                        WHERE entity_type = :entityType AND entity_id IN (:entityIds)
                        """)
                         .paramSource(new MapSqlParameterSource()
                                 .addValue("entityType", entityType)
                                 .addValue("entityIds",  entityIds))
                         .query(ROW_MAPPER)
                         .list();
    }

    public Set<Long> findEntityIdsByTaxonIds(@NonNull String entityType, @NonNull Set<Long> taxonIds) {
        return jdbcClient.sql("""
                        SELECT DISTINCT entity_id
                        FROM taxon_assignment
                        WHERE entity_type = :entityType AND taxon_id IN (:taxonIds)
                        """)
                         .paramSource(new MapSqlParameterSource()
                                 .addValue("entityType", entityType)
                                 .addValue("taxonIds",   taxonIds))
                         .query(Long.class)
                         .list()
                         .stream()
                         .collect(Collectors.toSet());
    }

    public long countByTaxonId(@NonNull Long taxonId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM taxon_assignment WHERE taxon_id = :taxonId")
                         .paramSource(new MapSqlParameterSource("taxonId", taxonId))
                         .query(Long.class)
                         .single();
    }

    public Map<Long, Long> countByTaxonIds(@NonNull Set<Long> taxonIds) {
        return jdbcClient.sql("""
                        SELECT taxon_id, COUNT(*) AS cnt
                        FROM taxon_assignment
                        WHERE taxon_id IN (:taxonIds)
                        GROUP BY taxon_id
                        """)
                         .paramSource(new MapSqlParameterSource("taxonIds", taxonIds))
                         .query((rs, _) -> Map.entry(rs.getLong("taxon_id"), rs.getLong("cnt")))
                         .list()
                         .stream()
                         .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
