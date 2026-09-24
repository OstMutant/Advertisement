package org.ost.contact.repository;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.contact.entity.ContactInfo;
import org.ost.contact.entity.ContactView;
import org.ost.platform.contact.dto.ContactViewCountDto;
import org.ost.platform.contact.model.ContactChannel;
import org.ost.platform.core.StaleWriteException;
import org.ost.platform.core.model.EntityType;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Bespoke {@code JdbcClient} queries for {@code contact_info}/{@code contact_view}; trivial CRUD delegates to {@link ContactInfoCrudRepository}/{@link ContactViewCrudRepository}. */
@Repository
@RequiredArgsConstructor
@SuppressWarnings("java:S1192")
public class ContactRepository {

    private static final RowMapper<ContactViewCountDto> COUNT_ROW_MAPPER = (rs, _) ->
            new ContactViewCountDto(ContactChannel.valueOf(rs.getString("channel")), rs.getLong("cnt"));

    private final JdbcClient jdbcClient;
    private final ContactInfoCrudRepository infoCrud;
    private final ContactViewCrudRepository viewCrud;

    public ContactInfo save(@NonNull ContactInfo info) {
        try {
            return infoCrud.save(info);
        } catch (OptimisticLockingFailureException e) {
            throw new StaleWriteException("ContactInfo " + info.getId() + " was modified by another session", e);
        }
    }

    private static final RowMapper<ContactInfo> ROW_MAPPER = (rs, _) -> ContactInfo.builder()
            .id(rs.getObject("id", Long.class))
            .entityType(EntityType.valueOf(rs.getString("entity_type")))
            .entityId(rs.getObject("entity_id", Long.class))
            .phone(rs.getString("phone"))
            .telegram(rs.getString("telegram"))
            .viber(rs.getString("viber"))
            .createdAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null)
            .updatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null)
            .version(rs.getObject("version", Long.class))
            .build();

    public Optional<ContactInfo> findByEntity(@NonNull EntityType entityType, @NonNull Long entityId) {
        return jdbcClient.sql("""
                        SELECT id, entity_type, entity_id, phone, telegram, viber, created_at, updated_at, version
                        FROM contact_info
                        WHERE entity_type = :entityType AND entity_id = :entityId
                        """)
                .paramSource(new MapSqlParameterSource()
                        .addValue("entityType", entityType.name())
                        .addValue("entityId", entityId))
                .query(ROW_MAPPER)
                .optional();
    }

    public void recordView(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull ContactChannel channel, Long viewerId) {
        viewCrud.save(ContactView.builder()
                .entityType(entityType)
                .entityId(entityId)
                .channel(channel)
                .viewerId(viewerId)
                .build());
    }

    public List<ContactViewCountDto> countViewsThisMonth(@NonNull EntityType entityType, @NonNull Long entityId) {
        return jdbcClient.sql("""
                        SELECT channel, COUNT(*) AS cnt
                        FROM contact_view
                        WHERE entity_type = :entityType AND entity_id = :entityId
                          AND created_at >= date_trunc('month', NOW())
                        GROUP BY channel
                        """)
                .paramSource(new MapSqlParameterSource()
                        .addValue("entityType", entityType.name())
                        .addValue("entityId", entityId))
                .query(COUNT_ROW_MAPPER)
                .list();
    }
}
