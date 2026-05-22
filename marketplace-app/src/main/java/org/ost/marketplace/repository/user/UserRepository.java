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
@SuppressWarnings("java:S1192")
public class UserRepository {

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
            SqlBoundFilter.of(name,           "u.name",       (m, v) -> like(m, v.getName())),
            SqlBoundFilter.of(email,          "u.email",      (m, v) -> like(m, v.getEmail())),
            SqlBoundFilter.of(roles,          "u.role",       (m, v) -> inSet(m, v.getRoles())),
            SqlBoundFilter.of(createdAtStart, "u.created_at", (m, v) -> after(m, v.getCreatedAtStart())),
            SqlBoundFilter.of(createdAtEnd,   "u.created_at", (m, v) -> before(m, v.getCreatedAtEnd())),
            SqlBoundFilter.of(updatedAtStart, "u.updated_at", (m, v) -> after(m, v.getUpdatedAtStart())),
            SqlBoundFilter.of(updatedAtEnd,   "u.updated_at", (m, v) -> before(m, v.getUpdatedAtEnd())),
            SqlBoundFilter.of(startId,        "u.id",         (m, v) -> after(m, v.getStartId())),
            SqlBoundFilter.of(endId,          "u.id",         (m, v) -> before(m, v.getEndId()))
    ));

    private static final SqlFilterBuilder<String> EMAIL_FILTER = new SqlFilterBuilder<>(List.of(
            SqlBoundFilter.of(email, "u.email", SqlCondition::equalsTo)
    ));

    private final JdbcClient jdbcClient;
    private final UserCrudRepository crud;

    public User save(User user)                { return crud.save(user); }
    public Optional<User> findById(Long id)    { return crud.findById(id); }
    public void deleteById(Long id)            { crud.deleteById(id); }

    public List<User> findByFilter(UserFilterDto filter, Pageable pageable) {
        var params = new MapSqlParameterSource();
        String orderBy = OrderByBuilder.build(pageable.getSort(), Map.of(
                "id",         "u.id",
                "name",       "u.name",
                "email",      "u.email",
                "role",       "u.role",
                "created_at", "u.created_at",
                "updated_at", "u.updated_at",
                "locale",     "u.locale"));
        String sql = "SELECT id, name, email, role, password_hash, created_at, updated_at, locale FROM user_information u%s%s%s"
                .formatted(FILTER.build(params, filter, " WHERE "), orderBy, pageLimit(params, pageable));
        return jdbcClient.sql(sql).paramSource(params).query(ROW_MAPPER).list();
    }

    public Long countByFilter(UserFilterDto filter) {
        var params = new MapSqlParameterSource();
        String sql = "SELECT COUNT(*) FROM user_information u%s".formatted(FILTER.build(params, filter, " WHERE "));
        return jdbcClient.sql(sql).paramSource(params).query(Long.class).single();
    }

    public Optional<User> findByEmail(String email) {
        var params = new MapSqlParameterSource();
        String sql = "SELECT id, name, email, role, password_hash, created_at, updated_at, locale FROM user_information u%s"
                .formatted(EMAIL_FILTER.build(params, email, " WHERE "));
        return jdbcClient.sql(sql).paramSource(params).query(ROW_MAPPER).optional();
    }

    public void updateProfile(UserProfileDto dto) {
        jdbcClient.sql("UPDATE user_information SET name = :name, role = :role, updated_at = NOW() WHERE id = :id")
                  .paramSource(new MapSqlParameterSource()
                          .addValue("name", dto.name())
                          .addValue("role", dto.role().name())
                          .addValue("id",   dto.id()))
                  .update();
    }

    public void updateLocale(Long userId, String locale) {
        jdbcClient.sql("UPDATE user_information SET locale = :locale WHERE id = :id")
                  .paramSource(new MapSqlParameterSource()
                          .addValue("locale", locale)
                          .addValue("id",     userId))
                  .update();
    }

    public List<Long> findExistingIds(Long[] ids) {
        return jdbcClient.sql("SELECT id FROM user_information WHERE id = ANY(:ids)")
                .paramSource(new MapSqlParameterSource("ids", ids))
                .query(Long.class)
                .list();
    }

    public Map<Long, String> findActorNames(Long[] ids) {
        return jdbcClient.sql("SELECT id, name FROM user_information WHERE id = ANY(:ids)")
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
