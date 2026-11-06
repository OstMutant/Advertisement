package org.ost.promotion.repository;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.promotion.dto.PromotionPlacementDto;
import org.ost.platform.promotion.model.PromotionReason;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Bespoke {@code JdbcClient} queries for {@code promotion_placement}; there is no trivial save or find, so no {@code CrudRepository}. */
@Repository
@RequiredArgsConstructor
@SuppressWarnings("java:S1192")
public class PromotionPlacementRepository {

    private static final RowMapper<PromotionPlacementDto> ROW_MAPPER = (rs, _) -> new PromotionPlacementDto(
            EntityType.valueOf(rs.getString("entity_type")),
            rs.getLong("entity_id"),
            PromotionReason.valueOf(rs.getString("reason")),
            rs.getTimestamp("valid_from").toInstant(),
            Optional.ofNullable(rs.getTimestamp("valid_until")).map(Timestamp::toInstant).orElse(null));

    private final JdbcClient jdbcClient;

    public List<PromotionPlacementDto> findActive(@NonNull EntityType entityType) {
        return jdbcClient.sql("""
                        SELECT entity_type, entity_id, reason, valid_from, valid_until
                        FROM promotion_placement
                        WHERE entity_type = :entityType AND reason IN (:reasons)
                          AND valid_from <= NOW() AND (valid_until IS NULL OR valid_until > NOW())
                        ORDER BY reason, entity_id
                        """)
                .paramSource(new MapSqlParameterSource()
                        .addValue("entityType", entityType.name())
                        .addValue("reasons", Arrays.stream(PromotionReason.values()).map(PromotionReason::name).toList()))
                .query(ROW_MAPPER)
                .list();
    }

    public void deleteForReasonExcept(@NonNull EntityType entityType, @NonNull PromotionReason reason, @NonNull Long[] ids) {
        jdbcClient.sql("""
                        DELETE FROM promotion_placement
                        WHERE entity_type = :entityType AND reason = :reason AND NOT (entity_id = ANY(:ids))
                        """)
                .paramSource(new MapSqlParameterSource()
                        .addValue("entityType", entityType.name())
                        .addValue("reason", reason.name())
                        .addValue("ids", ids))
                .update();
    }

    public void insertMissing(@NonNull EntityType entityType, @NonNull PromotionReason reason, @NonNull Long[] ids) {
        jdbcClient.sql("""
                        INSERT INTO promotion_placement (entity_type, entity_id, reason)
                        SELECT :entityType, unnest(:ids), :reason
                        ON CONFLICT (entity_type, entity_id, reason) DO NOTHING
                        """)
                .paramSource(new MapSqlParameterSource()
                        .addValue("entityType", entityType.name())
                        .addValue("reason", reason.name())
                        .addValue("ids", ids))
                .update();
    }

    public void deleteOne(@NonNull EntityType entityType, @NonNull PromotionReason reason, @NonNull Long entityId) {
        jdbcClient.sql("""
                        DELETE FROM promotion_placement
                        WHERE entity_type = :entityType AND reason = :reason AND entity_id = :entityId
                        """)
                .paramSource(new MapSqlParameterSource()
                        .addValue("entityType", entityType.name())
                        .addValue("reason", reason.name())
                        .addValue("entityId", entityId))
                .update();
    }
}
