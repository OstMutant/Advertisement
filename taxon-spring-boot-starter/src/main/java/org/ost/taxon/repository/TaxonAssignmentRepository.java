package org.ost.taxon.repository;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.core.model.SqlColumn;
import org.ost.platform.core.model.SqlPredicate;
import org.ost.taxon.entities.TaxonAssignment;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Bespoke {@code JdbcClient} queries for {@code taxon_assignment}: assign/unassign and the lookup shapes {@link org.ost.taxon.services.TaxonAssignmentService} needs. */
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
                              .build();
    };

    private final JdbcClient jdbcClient;

    public void assign(@NonNull String entityType, @NonNull Long entityId,
                       @NonNull Long taxonId) {
        jdbcClient.sql("""
                        INSERT INTO taxon_assignment (entity_type, entity_id, taxon_id, assigned_at)
                        VALUES (:entityType, :entityId, :taxonId, NOW())
                        ON CONFLICT (entity_type, entity_id, taxon_id) DO NOTHING
                        """)
                  .paramSource(new MapSqlParameterSource()
                          .addValue("entityType", entityType)
                          .addValue("entityId",   entityId)
                          .addValue("taxonId",    taxonId))
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

    public List<TaxonAssignment> findAllByEntity(@NonNull String entityType, @NonNull Long entityId) {
        return jdbcClient.sql("""
                        SELECT entity_type, entity_id, taxon_id, assigned_at
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
        // Array bind, not a Set -- avoids IN(:set)'s unbounded placeholder expansion.
        return jdbcClient.sql("""
                        SELECT entity_type, entity_id, taxon_id, assigned_at
                        FROM taxon_assignment
                        WHERE entity_type = :entityType AND entity_id = ANY(:entityIds)
                        """)
                         .paramSource(new MapSqlParameterSource()
                                 .addValue("entityType", entityType)
                                 .addValue("entityIds",  entityIds.toArray(new Long[0])))
                         .query(ROW_MAPPER)
                         .list();
    }

    /** Builds, without running it, the {@code EXISTS} restriction the consumer appends to its own statement; empty when neither filter is requested. */
    public Optional<SqlPredicate> categoryAndCityPredicate(@NonNull String entityType, @NonNull SqlColumn entityId,
                                                            Set<Long> categoryIds, Long cityTaxonId) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        if (categoryIds != null) {
            conditions.add("EXISTS (SELECT 1 FROM taxon_assignment _tac WHERE _tac.entity_type = :_taxonEntityType AND _tac.entity_id = "
                    + entityId.sql() + " AND _tac.taxon_id = ANY(:_taxonCategoryIds))");
            params.put("_taxonCategoryIds", categoryIds.toArray(new Long[0]));
        }
        if (cityTaxonId != null) {
            conditions.add("EXISTS (SELECT 1 FROM taxon_assignment _tay WHERE _tay.entity_type = :_taxonEntityType AND _tay.entity_id = "
                    + entityId.sql() + " AND _tay.taxon_id = :_taxonCityId)");
            params.put("_taxonCityId", cityTaxonId);
        }
        if (conditions.isEmpty()) return Optional.empty();
        params.put("_taxonEntityType", entityType);
        return Optional.of(new SqlPredicate(String.join(" AND ", conditions), params));
    }

    public Map<Long, Long> countByTaxonIds(@NonNull Set<Long> taxonIds) {
        return jdbcClient.sql("""
                        SELECT taxon_id, COUNT(*) AS cnt
                        FROM taxon_assignment
                        WHERE taxon_id = ANY(:taxonIds)
                        GROUP BY taxon_id
                        """)
                         .paramSource(new MapSqlParameterSource("taxonIds", taxonIds.toArray(new Long[0])))
                         .query((rs, _) -> Map.entry(rs.getLong("taxon_id"), rs.getLong("cnt")))
                         .list()
                         .stream()
                         .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
