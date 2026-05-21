package org.ost.marketplace.repository.user;

import lombok.RequiredArgsConstructor;
import org.ost.marketplace.dto.UserProfileDto;
import org.ost.marketplace.dto.filter.UserFilterDto;
import org.ost.marketplace.entities.Role;
import org.ost.marketplace.entities.User;
import org.ost.sqlengine.filter.SqlBoundFilter;
import org.ost.sqlengine.filter.SqlCondition;
import org.ost.sqlengine.filter.SqlFilterBuilder;
import org.ost.sqlengine.sort.OrderByBuilder;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.ost.marketplace.dto.filter.UserFilterDto.Fields.*;
import static org.ost.sqlengine.filter.SqlCondition.*;

@Repository
@RequiredArgsConstructor
public class UserRepository {

    private static final String TABLE  = "user_information";
    private static final String ALIAS  = "u";

    private static final String SELECT =
            "SELECT id, name, email, role, password_hash, created_at, updated_at, locale" +
            " FROM " + TABLE + " " + ALIAS;
    private static final String COUNT  = "SELECT COUNT(*) FROM " + TABLE + " " + ALIAS;

    private static final String SELECT_EXISTING_IDS =
            "SELECT id FROM " + TABLE + " WHERE id = ANY(:ids)";
    private static final String SELECT_ACTOR_NAMES =
            "SELECT id, name FROM " + TABLE + " WHERE id = ANY(:ids)";

    private static final String UPDATE_PROFILE =
            "UPDATE " + TABLE + " SET name = :name, role = :role, updated_at = NOW() WHERE id = :id";
    private static final String UPDATE_LOCALE =
            "UPDATE " + TABLE + " SET locale = :locale WHERE id = :id";

    private static final Map<String, String> SORT = Map.of(
            "id",         ALIAS + ".id",
            "name",       ALIAS + ".name",
            "email",      ALIAS + ".email",
            "role",       ALIAS + ".role",
            "created_at", ALIAS + ".created_at",
            "updated_at", ALIAS + ".updated_at",
            "locale",     ALIAS + ".locale");

    private static final RowMapper<User> ROW_MAPPER = (rs, _) -> {
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        return User.builder()
                .id(rs.getObject("id", Long.class))
                .name(rs.getString("name"))
                .email(rs.getString("email"))
                .role(Role.valueOf(rs.getString("role")))
                .passwordHash(rs.getString("password_hash"))
                .createdAt(createdAt != null ? createdAt.toInstant() : null)
                .updatedAt(updatedAt != null ? updatedAt.toInstant() : null)
                .locale(rs.getString("locale"))
                .build();
    };

    private static final SqlFilterBuilder<UserFilterDto> FILTER = new SqlFilterBuilder<>(List.of(
            SqlBoundFilter.of(name,           ALIAS + ".name",       (m, v) -> like(m, v.getName())),
            SqlBoundFilter.of(email,          ALIAS + ".email",      (m, v) -> like(m, v.getEmail())),
            SqlBoundFilter.of(roles,          ALIAS + ".role",       (m, v) -> inSet(m, v.getRoles())),
            SqlBoundFilter.of(createdAtStart, ALIAS + ".created_at", (m, v) -> after(m, v.getCreatedAtStart())),
            SqlBoundFilter.of(createdAtEnd,   ALIAS + ".created_at", (m, v) -> before(m, v.getCreatedAtEnd())),
            SqlBoundFilter.of(updatedAtStart, ALIAS + ".updated_at", (m, v) -> after(m, v.getUpdatedAtStart())),
            SqlBoundFilter.of(updatedAtEnd,   ALIAS + ".updated_at", (m, v) -> before(m, v.getUpdatedAtEnd())),
            SqlBoundFilter.of(startId,        ALIAS + ".id",         (m, v) -> after(m, v.getStartId())),
            SqlBoundFilter.of(endId,          ALIAS + ".id",         (m, v) -> before(m, v.getEndId()))
    ));

    private static final SqlFilterBuilder<String> EMAIL_FILTER = new SqlFilterBuilder<>(List.of(
            SqlBoundFilter.of(email, ALIAS + ".email", SqlCondition::equalsTo)
    ));

    private final JdbcClient jdbcClient;
    private final UserCrudRepository crud;

    public User save(User user)                { return crud.save(user); }
    public Optional<User> findById(Long id)    { return crud.findById(id); }
    public void deleteById(Long id)            { crud.deleteById(id); }

    public List<User> findByFilter(UserFilterDto filter, Pageable pageable) {
        var params = new MapSqlParameterSource();
        String where   = FILTER.build(params, filter);
        String orderBy = OrderByBuilder.build(pageable.getSort(), SORT);
        String sql = SELECT
                + (where.isBlank()   ? "" : " WHERE " + where)
                + (orderBy.isBlank() ? "" : " " + orderBy)
                + pageLimit(params, pageable);
        return jdbcClient.sql(sql).paramSource(params).query(ROW_MAPPER).list();
    }

    public Long countByFilter(UserFilterDto filter) {
        var params = new MapSqlParameterSource();
        String where = FILTER.build(params, filter);
        String sql = COUNT + (where.isBlank() ? "" : " WHERE " + where);
        return jdbcClient.sql(sql).paramSource(params).query(Long.class).single();
    }

    public Optional<User> findByEmail(String email) {
        var params = new MapSqlParameterSource();
        String where = EMAIL_FILTER.build(params, email);
        String sql = SELECT + (where.isBlank() ? "" : " WHERE " + where);
        return jdbcClient.sql(sql).paramSource(params).query(ROW_MAPPER).optional();
    }

    public void updateProfile(UserProfileDto dto) {
        jdbcClient.sql(UPDATE_PROFILE)
                  .paramSource(new MapSqlParameterSource()
                          .addValue("name", dto.name())
                          .addValue("role", dto.role().name())
                          .addValue("id",   dto.id()))
                  .update();
    }

    public void updateLocale(Long userId, String locale) {
        jdbcClient.sql(UPDATE_LOCALE)
                  .paramSource(new MapSqlParameterSource()
                          .addValue("locale", locale)
                          .addValue("id",     userId))
                  .update();
    }

    public List<Long> findExistingIds(Long[] ids) {
        return jdbcClient.sql(SELECT_EXISTING_IDS)
                .paramSource(new MapSqlParameterSource("ids", ids))
                .query(Long.class)
                .list();
    }

    public Map<Long, String> findActorNames(Long[] ids) {
        return jdbcClient.sql(SELECT_ACTOR_NAMES)
                .paramSource(new MapSqlParameterSource("ids", ids))
                .query((rs, _) -> Map.entry(rs.getObject("id", Long.class), rs.getString("name")))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static String pageLimit(MapSqlParameterSource params, Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) return "";
        params.addValue("limit",  pageable.getPageSize());
        params.addValue("offset", pageable.getOffset());
        return " LIMIT :limit OFFSET :offset";
    }
}
