package org.ost.user.repository;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.dto.UserFilterDto;
import org.ost.platform.user.dto.UserProfileDto;
import org.ost.platform.user.model.Role;
import org.ost.query.filter.SqlBoundFilter;
import org.ost.query.filter.SqlCondition;
import org.ost.query.filter.SqlFilterBuilder;
import org.ost.query.sort.OrderByBuilder;
import org.ost.query.sort.PaginationSqlBuilder;
import org.ost.user.entity.User;
import org.ost.user.entity.UserProfileUpdate;
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
import java.util.stream.Collectors;

import static org.ost.platform.user.dto.UserFilterDto.Fields.*;
import static org.ost.query.filter.SqlCondition.*;

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
                .version(rs.getObject("version", Long.class))
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
    private final UserProfileCrudRepository profileCrud;

    public User save(@NonNull User user)                { return crud.save(user); }
    public Optional<User> findById(@NonNull Long id)    { return crud.findById(id); }
    public void deleteById(@NonNull Long id)            { crud.deleteById(id); }

    public List<User> findByFilter(@NonNull UserFilterDto filter, @NonNull Pageable pageable) {
        var params = new MapSqlParameterSource();
        String orderBy = OrderByBuilder.build(pageable.getSort(), Map.of(
                UserDto.Fields.id,        "u.id",
                UserDto.Fields.name,      "u.name",
                UserDto.Fields.email,     "u.email",
                UserDto.Fields.role,      "u.role",
                UserDto.Fields.createdAt, "u.created_at",
                UserDto.Fields.updatedAt, "u.updated_at",
                UserDto.Fields.locale,    "u.locale"));
        String sql = "SELECT id, name, email, role, password_hash, created_at, updated_at, locale, version FROM user_information u WHERE u.deleted_at IS NULL%s%s%s"
                .formatted(FILTER.build(params, filter, " AND "), orderBy, PaginationSqlBuilder.pageLimit(params, pageable));
        return jdbcClient.sql(sql).paramSource(params).query(ROW_MAPPER).list();
    }

    public Long countByFilter(@NonNull UserFilterDto filter) {
        var params = new MapSqlParameterSource();
        String sql = "SELECT COUNT(*) FROM user_information u WHERE u.deleted_at IS NULL%s".formatted(FILTER.build(params, filter, " AND "));
        return jdbcClient.sql(sql).paramSource(params).query(Long.class).single();
    }

    public Optional<User> findByEmail(@NonNull String email) {
        var params = new MapSqlParameterSource();
        String sql = "SELECT id, name, email, role, password_hash, created_at, updated_at, locale, version FROM user_information u WHERE u.deleted_at IS NULL%s"
                .formatted(EMAIL_FILTER.build(params, email, " AND "));
        return jdbcClient.sql(sql).paramSource(params).query(ROW_MAPPER).optional();
    }

    public void softDelete(@NonNull Long id, @NonNull Long deletedByUserId) {
        jdbcClient.sql("UPDATE user_information SET deleted_at = NOW(), deleted_by = :deletedBy WHERE id = :id AND deleted_at IS NULL")
                  .paramSource(new MapSqlParameterSource()
                          .addValue("id",        id)
                          .addValue("deletedBy", deletedByUserId))
                  .update();
    }

    public Set<Long> findDeletedIds(@NonNull Long[] ids) {
        return Set.copyOf(jdbcClient.sql("SELECT id FROM user_information WHERE id = ANY(:ids) AND deleted_at IS NOT NULL")
                .paramSource(new MapSqlParameterSource("ids", ids))
                .query(Long.class)
                .list());
    }

    public List<Long> findIdsDeletedOlderThan(int days) {
        return jdbcClient.sql("SELECT id FROM user_information WHERE deleted_at < NOW() - MAKE_INTERVAL(days => :days)")
                .paramSource(new MapSqlParameterSource("days", days))
                .query(Long.class)
                .list();
    }

    public void updateProfile(@NonNull UserProfileDto dto) {
        profileCrud.save(UserProfileUpdate.builder()
                .id(dto.id())
                .name(dto.name())
                .role(dto.role())
                .version(dto.version())
                .build());
    }

    public void updateLocale(@NonNull Long userId, @NonNull String locale) {
        jdbcClient.sql("UPDATE user_information SET locale = :locale WHERE id = :id")
                  .paramSource(new MapSqlParameterSource()
                          .addValue("locale", locale)
                          .addValue("id",     userId))
                  .update();
    }

    public List<Long> findExistingIds(@NonNull Long[] ids) {
        return jdbcClient.sql("SELECT id FROM user_information WHERE id = ANY(:ids)")
                .paramSource(new MapSqlParameterSource("ids", ids))
                .query(Long.class)
                .list();
    }

    public Map<Long, String> findActorNames(@NonNull Long[] ids) {
        return jdbcClient.sql("SELECT id, name FROM user_information WHERE id = ANY(:ids)")
                .paramSource(new MapSqlParameterSource("ids", ids))
                .query((rs, _) -> Map.entry(rs.getObject("id", Long.class), rs.getString("name")))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public List<User> findByIds(@NonNull Long[] ids) {
        return jdbcClient.sql("SELECT id, name, email, role, password_hash, created_at, updated_at, locale, version FROM user_information WHERE id = ANY(:ids)")
                .paramSource(new MapSqlParameterSource("ids", ids))
                .query(ROW_MAPPER)
                .list();
    }
}
