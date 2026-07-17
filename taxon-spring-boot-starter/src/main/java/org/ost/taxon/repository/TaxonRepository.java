package org.ost.taxon.repository;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.taxon.model.TaxonType;
import org.ost.query.filter.SqlBoundFilter;
import org.ost.query.filter.SqlFilterBuilder;
import org.ost.query.sort.OrderByBuilder;
import org.ost.taxon.entities.Taxon;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.ost.query.filter.SqlCondition.like;

@Repository
@RequiredArgsConstructor
@SuppressWarnings("java:S1192")
public class TaxonRepository {

    private static final RowMapper<Taxon> ROW_MAPPER = (rs, _) -> {
        String    typeName  = rs.getString("type");
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        Timestamp deletedAt = rs.getTimestamp("deleted_at");
        return Taxon.builder()
                    .id(rs.getObject("id", Long.class))
                    .type(typeName == null ? null : TaxonType.valueOf(typeName))
                    .code(rs.getString("code"))
                    .deletedAt(deletedAt != null ? deletedAt.toInstant() : null)
                    .deletedBy(rs.getObject("deleted_by", Long.class))
                    .createdAt(createdAt != null ? createdAt.toInstant() : null)
                    .updatedAt(updatedAt != null ? updatedAt.toInstant() : null)
                    .createdBy(rs.getObject("created_by", Long.class))
                    .updatedBy(rs.getObject("updated_by", Long.class))
                    .version(rs.getObject("version", Long.class))
                    .build();
    };

    private static final SqlFilterBuilder<TaxonFilter> FILTER = new SqlFilterBuilder<>(List.of(
            SqlBoundFilter.of("name", "tt.name", (m, f) -> like(m, f.name()))
    ));

    private static final Map<String, String> SORT_ALIASES = Map.of(
            Taxon.Fields.id,        "t.id",
            Taxon.Fields.createdAt, "t.created_at",
            Taxon.Fields.updatedAt, "t.updated_at"
    );

    private final TaxonCrudRepository crud;
    private final JdbcClient          jdbcClient;

    public Taxon save(@NonNull Taxon taxon) {
        return crud.save(taxon);
    }

    public Optional<Taxon> findById(@NonNull Long id) {
        return crud.findById(id);
    }

    public List<Taxon> findAllByType(@NonNull TaxonType type, @NonNull TaxonFilter filter, @NonNull Sort sort) {
        var    params  = new MapSqlParameterSource().addValue("type", type.name());
        String dynamic = FILTER.build(params, filter, " AND ");
        String deleted = filter.showDeleted() ? "" : " AND t.deleted_at IS NULL";
        String orderBy = OrderByBuilder.build(sort, SORT_ALIASES);
        return jdbcClient.sql("""
                        SELECT t.id, t.type, t.code, t.deleted_at, t.deleted_by, t.created_at, t.updated_at,
                               t.created_by, t.updated_by, t.version
                        FROM taxon t
                        LEFT JOIN taxon_translation tt ON tt.taxon_id = t.id AND tt.locale = 'en'
                        WHERE t.type = :type
                        """ + deleted + dynamic + " " + orderBy)
                         .paramSource(params)
                         .query(ROW_MAPPER)
                         .list();
    }

    public List<Taxon> findByIds(@NonNull Set<Long> ids) {
        return jdbcClient.sql("""
                        SELECT id, type, code, deleted_at, deleted_by, created_at, updated_at, created_by, updated_by, version
                        FROM taxon
                        WHERE id IN (:ids) AND deleted_at IS NULL
                        """)
                         .paramSource(new MapSqlParameterSource("ids", ids))
                         .query(ROW_MAPPER)
                         .list();
    }

    public void softDelete(@NonNull Long id, Long actorId, Long version) {
        int updated = jdbcClient.sql("""
                        UPDATE taxon SET deleted_at = NOW(), deleted_by = :deletedBy, version = version + 1
                        WHERE id = :id AND version = :version
                        """)
                  .paramSource(new MapSqlParameterSource()
                          .addValue("id",        id)
                          .addValue("deletedBy", actorId)
                          .addValue("version",   version))
                  .update();
        if (updated == 0) {
            throw new OptimisticLockingFailureException("Taxon " + id + " was modified by another session");
        }
    }

    public void restore(@NonNull Long id) {
        jdbcClient.sql("UPDATE taxon SET deleted_at = NULL WHERE id = :id")
                  .paramSource(new MapSqlParameterSource("id", id))
                  .update();
    }

    public long countByType(@NonNull TaxonType type) {
        return jdbcClient.sql("SELECT COUNT(*) FROM taxon WHERE type = :type AND deleted_at IS NULL")
                         .paramSource(new MapSqlParameterSource("type", type.name()))
                         .query(Long.class)
                         .single();
    }

    public Set<Long> findExistingIds(@NonNull Set<Long> ids) {
        return jdbcClient.sql("SELECT id FROM taxon WHERE id IN (:ids)")
                         .paramSource(new MapSqlParameterSource("ids", ids))
                         .query(Long.class)
                         .list()
                         .stream()
                         .collect(java.util.stream.Collectors.toSet());
    }
}
