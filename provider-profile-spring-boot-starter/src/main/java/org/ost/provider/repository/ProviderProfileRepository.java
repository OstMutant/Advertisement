package org.ost.provider.repository;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.dto.ProviderProfileFilterDto;
import org.ost.platform.providerprofile.model.ProviderKind;
import org.ost.provider.entity.ProviderProfile;
import org.ost.query.filter.SqlBoundFilter;
import org.ost.query.filter.SqlFilterBuilder;
import org.ost.query.sort.OrderByBuilder;
import org.ost.query.sort.PaginationSqlBuilder;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.ost.platform.providerprofile.dto.ProviderProfileFilterDto.Fields.*;
import static org.ost.query.filter.SqlCondition.*;

@Repository
@RequiredArgsConstructor
@SuppressWarnings("java:S1192")
public class ProviderProfileRepository {

    private static final RowMapper<ProviderProfileDto> ROW_MAPPER = (rs, _) -> {
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        return ProviderProfileDto.builder()
                .id(rs.getObject("id", Long.class))
                .actorId(rs.getObject("actor_id", Long.class))
                .kind(ProviderKind.valueOf(rs.getString("kind")))
                .about(rs.getString("about"))
                .cityTaxonId(rs.getObject("city_taxon_id", Long.class))
                .createdAt(createdAt != null ? createdAt.toInstant() : null)
                .updatedAt(updatedAt != null ? updatedAt.toInstant() : null)
                .version(rs.getObject("version", Long.class))
                .build();
    };

    private static final SqlFilterBuilder<ProviderProfileFilterDto> FILTER = new SqlFilterBuilder<>(List.of(
            SqlBoundFilter.of(kinds,        "pp.kind",          (m, v) -> inSet(m, v.getKinds())),
            SqlBoundFilter.of(cityTaxonId,  "pp.city_taxon_id", (m, v) -> equalsTo(m, v.getCityTaxonId()))
    ));

    private static final String SELECT = """
            SELECT pp.id, pp.actor_id, pp.kind, pp.about, pp.city_taxon_id, pp.created_at, pp.updated_at, pp.version
            FROM provider_profile pp
            """;

    private final JdbcClient jdbcClient;
    private final ProviderProfileCrudRepository crud;

    public ProviderProfile save(@NonNull ProviderProfile profile) { return crud.save(profile); }
    public Optional<ProviderProfile> findById(@NonNull Long id)   { return crud.findById(id); }

    public Optional<ProviderProfileDto> findProviderProfileById(@NonNull Long id) {
        return jdbcClient.sql(SELECT + "WHERE pp.id = :id")
                .paramSource(new MapSqlParameterSource("id", id))
                .query(ROW_MAPPER).optional();
    }

    public Optional<ProviderProfileDto> findByActorId(@NonNull Long actorId) {
        return jdbcClient.sql(SELECT + "WHERE pp.actor_id = :actorId")
                .paramSource(new MapSqlParameterSource("actorId", actorId))
                .query(ROW_MAPPER).optional();
    }

    public List<ProviderProfileDto> findByFilter(@NonNull ProviderProfileFilterDto filter, @NonNull Pageable pageable,
                                                  Set<Long> allowedIds) {
        var params = new MapSqlParameterSource();
        String orderBy = OrderByBuilder.build(pageable.getSort(), Map.ofEntries(
                Map.entry(ProviderProfileDto.Fields.id,        "pp.id"),
                Map.entry(ProviderProfileDto.Fields.kind,      "pp.kind"),
                Map.entry(ProviderProfileDto.Fields.createdAt, "pp.created_at"),
                Map.entry(ProviderProfileDto.Fields.updatedAt, "pp.updated_at")));
        String sql = (SELECT + "WHERE 1=1%s%s%s%s")
                .formatted(buildIdClause(params, allowedIds), FILTER.build(params, filter, " AND "), orderBy, PaginationSqlBuilder.pageLimit(params, pageable));
        return jdbcClient.sql(sql).paramSource(params).query(ROW_MAPPER).list();
    }

    public Long countByFilter(@NonNull ProviderProfileFilterDto filter, Set<Long> allowedIds) {
        var params = new MapSqlParameterSource();
        String sql = "SELECT COUNT(*) FROM provider_profile pp WHERE 1=1%s%s"
                .formatted(buildIdClause(params, allowedIds), FILTER.build(params, filter, " AND "));
        return jdbcClient.sql(sql).paramSource(params).query(Long.class).single();
    }

    private static String buildIdClause(MapSqlParameterSource params, Set<Long> ids) {
        if (ids == null) return "";
        params.addValue("allowedIds", ids.toArray(new Long[0]));
        return " AND pp.id = ANY(:allowedIds)";
    }

    public void delete(@NonNull Long id, Long version) {
        int updated = jdbcClient.sql("DELETE FROM provider_profile WHERE id = :id AND version = :version")
                .paramSource(new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("version", version))
                .update();
        if (updated == 0) {
            throw new OptimisticLockingFailureException("ProviderProfile " + id + " was modified by another session");
        }
    }

    public List<Long> findExistingIds(@NonNull Long[] ids) {
        return jdbcClient.sql("SELECT id FROM provider_profile WHERE id = ANY(:ids)")
                .paramSource(new MapSqlParameterSource("ids", ids))
                .query(Long.class)
                .list();
    }

    public Set<Long> findOwnerIds(@NonNull Set<Long> userIds) {
        return Set.copyOf(jdbcClient.sql("SELECT DISTINCT actor_id FROM provider_profile WHERE actor_id = ANY(:ids)")
                .paramSource(new MapSqlParameterSource("ids", userIds.toArray(new Long[0])))
                .query(Long.class)
                .list());
    }

}
